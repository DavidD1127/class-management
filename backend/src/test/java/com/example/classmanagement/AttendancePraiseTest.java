package com.example.classmanagement;

import com.example.classmanagement.business.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AttendancePraiseTest {
    @Autowired AttendanceService attendance; @Autowired PraiseService praises; @Autowired BusinessRepository db; @Autowired MockMvc mvc;
    long c,s;
    @BeforeEach void setup(){
        c=db.insert("INSERT INTO classes(class_name,grade,academic_year,created_at,updated_at) VALUES('考勤表扬班','高一','test',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        s=db.insert("INSERT INTO students(student_no,name,class_id,status,created_at,updated_at) VALUES('AP001','虚构学生',?,'在读',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",c);
    }
    AttendanceService.Input att(String type,boolean confirm){return new AttendanceService.Input(s,type,LocalDate.now(),LocalTime.of(8,15),"说明",confirm);}
    @Test @WithMockUser(username="real-admin",roles="ADMIN") void attendanceCrudDuplicatesAndStatistics(){
        long id=BusinessRules.id(attendance.save(null,att("LATE",false)).get("id"));
        assertThatThrownBy(()->attendance.save(null,att("LATE",false))).hasMessageContaining("同类考勤");
        attendance.save(null,att("EARLY_LEAVE",false));attendance.save(null,att("ABSENT",false));attendance.save(null,att("LATE",true));
        assertThat(attendance.list(s,c,LocalDate.now(),LocalDate.now(),null)).hasSize(4);
        assertThat(attendance.statistics(s,c,null,null).get(0)).containsEntry("LATE",2L).containsEntry("EARLY_LEAVE",1L).containsEntry("ABSENT",1L);
        attendance.save(id,new AttendanceService.Input(s,"LATE",LocalDate.now().minusDays(1),null,"修改",false));
        assertThat(attendance.list(s,c,LocalDate.now().minusDays(1),LocalDate.now().minusDays(1),"LATE")).hasSize(1);
        attendance.delete(id);assertThat(attendance.list(s,c,null,null,null)).hasSize(3);
        assertThatThrownBy(()->attendance.save(null,new AttendanceService.Input(-1L,"LATE",LocalDate.now(),null,"",false))).hasMessageContaining("不存在");
    }
    @Test @WithMockUser(username="real-admin",roles="ADMIN") void praiseMultipleRecordsAndValidation(){
        var i=new PraiseService.Input(s,LocalDate.now(),"帮助同学","");
        long id=BusinessRules.id(praises.save(null,i).get("id"));praises.save(null,new PraiseService.Input(s,LocalDate.now(),"认真作业",""));
        assertThat(praises.statistics(s,c,null,null).get(0)).containsEntry("count",2L);
        assertThat(praises.save(id,new PraiseService.Input(s,LocalDate.now(),"主动帮助同学",""))).containsEntry("reason","主动帮助同学").containsEntry("recorder","real-admin");
        praises.delete(id);assertThat(praises.list(s,c,LocalDate.now(),LocalDate.now())).hasSize(1);
        assertThatThrownBy(()->praises.save(null,new PraiseService.Input(s,null,"原因",""))).hasMessageContaining("日期");
        assertThatThrownBy(()->praises.save(null,new PraiseService.Input(s,LocalDate.now()," ",""))).hasMessageContaining("原因");
        assertThatThrownBy(()->praises.save(null,new PraiseService.Input(-1L,LocalDate.now(),"原因",""))).hasMessageContaining("不存在");
    }
    @Test void anonymousCannotAccessAnyModule()throws Exception {
        for(String path:List.of("/api/exams","/api/scores","/api/assignments","/api/attendance","/api/praises"))mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
    @Test @WithMockUser(roles="TEACHER") void teacherTraditionalEndpointsRestrictedButTeachingServicesAllowed()throws Exception {
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        for(String path:List.of("/api/exams","/api/scores","/api/assignments","/api/attendance","/api/praises")){
            mvc.perform(get(path)).andExpect(status().isOk());
            mvc.perform(post(path).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        }
        for(String path:List.of("/api/attendance/1","/api/praises/1"))mvc.perform(delete(path)).andExpect(status().isForbidden());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        assertThat(attendance.save(null,att("LATE",false))).containsKey("id");
        assertThat(praises.save(null,new PraiseService.Input(s,LocalDate.now(),"原因",""))).containsKey("id");
        assertThatThrownBy(()->attendance.delete(1L)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(()->praises.delete(1L)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test @WithMockUser(username="real-admin",roles="ADMIN") void clientCannotForgeRecorderAndDatesValidated()throws Exception {
        String body="{\"studentId\":"+s+",\"type\":\"LATE\",\"occurredDate\":\"2026-10-21\",\"recorder\":\"forged\"}";
        mvc.perform(post("/api/attendance").contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.data.recorder").value("real-admin"));
        mvc.perform(post("/api/praises").contentType("application/json").content("{\"studentId\":"+s+",\"praiseDate\":\"2026-10-21\",\"reason\":\"认真学习\",\"recorder\":\"forged\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.recorder").value("real-admin"));
        mvc.perform(post("/api/attendance").contentType("application/json").content(body.replace("2026-10-21","2026-02-30"))).andExpect(status().isBadRequest());
    }
}
