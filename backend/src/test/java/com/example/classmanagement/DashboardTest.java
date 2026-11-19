package com.example.classmanagement;

import com.example.classmanagement.business.BusinessRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class DashboardTest {
    @Autowired MockMvc mvc;
    @Autowired BusinessRepository db;
    long c,s;
    @BeforeEach void setup(){
        c=db.insert("INSERT INTO classes(class_name,grade,academic_year,created_at,updated_at) VALUES('仪表盘测试班','高一','test',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        s=db.insert("INSERT INTO students(student_no,name,class_id,status,created_at,updated_at) VALUES('DASH001','虚构测试学生',?,'在读',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",c);
    }
    String url(){return "/api/dashboard?classId="+c+"&from=2026-10-01&to=2026-10-21";}
    @Test @WithMockUser(roles="TEACHER") void aggregatesOnlySelectedRangeAndExcludesAbsentScoresAndExcludedAssignments()throws Exception{
        long exam=db.insert("INSERT INTO exams(name,class_id,exam_date) VALUES('测试考试',?,'2026-10-20')",c);
        db.update("INSERT INTO scores(student_id,exam_id,subject,score,full_score,status) VALUES(?,?,'数学',0,100,'RECORDED')",s,exam);
        db.update("INSERT INTO scores(student_id,exam_id,subject,score,full_score,status) VALUES(?,?,'语文',NULL,150,'ABSENT')",s,exam);
        long assignment=db.insert("INSERT INTO assignments(name,class_id,subject,assigned_date,deadline) VALUES('测试作业',?,'数学','2026-10-20','2026-10-21 18:00:00')",c);
        db.update("INSERT INTO assignment_records(assignment_id,student_id,status,included) VALUES(?,?,'COMPLETED',TRUE)",assignment,s);
        long excluded=db.insert("INSERT INTO assignments(name,class_id,subject,assigned_date,deadline) VALUES('排除作业',?,'数学','2026-10-20','2026-10-21 18:00:00')",c);
        db.update("INSERT INTO assignment_records(assignment_id,student_id,status,included) VALUES(?,?,'INCOMPLETE',FALSE)",excluded,s);
        db.update("INSERT INTO attendance(student_id,type,occurred_date,recorder) VALUES(?,'LATE','2026-10-20','test')",s);
        db.update("INSERT INTO attendance(student_id,type,occurred_date,recorder) VALUES(?,'ABSENT','2026-09-20','test')",s);
        db.update("INSERT INTO praises(student_id,praise_date,reason,recorder) VALUES(?,'2026-10-20','测试','test')",s);
        mvc.perform(get(url())).andExpect(status().isOk()).andExpect(jsonPath("$.data.students").value(1)).andExpect(jsonPath("$.data.classes").value(1))
            .andExpect(jsonPath("$.data.examAverages.length()").value(1)).andExpect(jsonPath("$.data.examAverages[0].averageScore").value(0))
            .andExpect(jsonPath("$.data.assignmentStates.length()").value(1)).andExpect(jsonPath("$.data.assignmentStates[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.attendance.length()").value(1)).andExpect(jsonPath("$.data.praises").value(1));
    }
    @Test @WithMockUser(roles="ADMIN") void emptyDataAndInvalidRanges()throws Exception{
        mvc.perform(get(url())).andExpect(status().isOk()).andExpect(jsonPath("$.data.examAverages").isEmpty()).andExpect(jsonPath("$.data.attendance").isEmpty()).andExpect(jsonPath("$.data.assignmentStates").isEmpty());
        mvc.perform(get("/api/dashboard?from=2026-10-21&to=2026-10-01")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard?from=2020-01-01&to=2026-10-21")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard?from=2026-10-01")).andExpect(status().isBadRequest());
    }
    @Test void anonymousDenied()throws Exception{mvc.perform(get(url())).andExpect(status().isUnauthorized());}
    @Test @WithMockUser(roles="STUDENT") void unprivilegedDenied()throws Exception{mvc.perform(get(url())).andExpect(status().isForbidden());}
}
