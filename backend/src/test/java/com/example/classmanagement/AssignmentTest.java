package com.example.classmanagement;

import com.example.classmanagement.business.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional @WithMockUser(roles="ADMIN")
class AssignmentTest {
    @Autowired AssignmentService service; @Autowired BusinessRepository db;
    long c,s,a;
    @BeforeEach void setup(){
        c=db.insert("INSERT INTO classes(class_name,grade,academic_year,created_at,updated_at) VALUES('作业测试班','高一','test',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        s=db.insert("INSERT INTO students(student_no,name,class_id,status,created_at,updated_at) VALUES('HW001','虚构学生',?,'在读',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",c);
        a=BusinessRules.id(service.save(null,new AssignmentService.Input("练习",c,"数学","说明",LocalDate.now().minusDays(3),LocalDateTime.now().minusDays(1))).get("id"));
    }
    @Test void snapshotsAndDistinguishesUnconfirmedAndComputesRate(){
        assertThat(service.records(a,null,null,null,null,null,"UNCONFIRMED")).hasSize(1);
        assertThat(service.records(a,null,null,null,null,null,"INCOMPLETE")).isEmpty();
        service.state(a,s,new AssignmentService.StateInput("INCOMPLETE",null,"确认未完成",true));
        assertThat(service.records(a,s,c,"数学",LocalDate.now().minusDays(5),LocalDate.now(),"INCOMPLETE")).hasSize(1);
        assertThat((BigDecimal)service.statistics(a).get("completionRate")).isEqualByComparingTo("0");
        service.state(a,s,new AssignmentService.StateInput("COMPLETED",LocalDateTime.now().minusHours(2),"补交",true));
        assertThat(service.records(a,s,null,null,null,null,"LATE")).hasSize(1);
        assertThat((BigDecimal)service.statistics(a).get("completionRate")).isEqualByComparingTo("100");
        service.state(a,s,new AssignmentService.StateInput("LATE",LocalDateTime.now().minusHours(2),"不计入",false));
        assertThat(service.statistics(a)).containsEntry("denominator",0).containsEntry("completionRate",null);
    }
    @Test void validatesRelationsAndTimes(){
        assertThatThrownBy(()->service.state(a,-1L,new AssignmentService.StateInput("COMPLETED",LocalDateTime.now(),"",true))).hasMessageContaining("不存在");
        assertThatThrownBy(()->service.state(-1L,s,new AssignmentService.StateInput("INCOMPLETE",null,"",true))).hasMessageContaining("不存在");
        assertThatThrownBy(()->service.state(a,s,new AssignmentService.StateInput("COMPLETED",null,"",true))).hasMessageContaining("提交时间");
        assertThatThrownBy(()->service.state(a,s,new AssignmentService.StateInput("PENDING",null,"",true))).hasMessageContaining("已到期");
        assertThatThrownBy(()->service.save(a,new AssignmentService.Input("练习",c,"数学","",LocalDate.now(),LocalDateTime.now().minusDays(1)))).hasMessageContaining("截止");
    }
}
