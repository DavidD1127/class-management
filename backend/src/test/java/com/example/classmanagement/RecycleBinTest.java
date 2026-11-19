package com.example.classmanagement;

import com.example.classmanagement.recycle.RecycleBinService;
import com.example.classmanagement.business.BusinessRepository;
import com.example.classmanagement.ai.WriteTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class RecycleBinTest {
    @Autowired RecycleBinService recycle;@Autowired BusinessRepository db;@Autowired WriteTools writes;@Autowired ObjectMapper json;@Autowired MockMvc mvc;
    long classroom,student,attendance;
    @BeforeEach void setup(){
        db.update("DELETE FROM recycle_items");db.update("DELETE FROM recycle_batches");
        db.insert("INSERT INTO users(username,password,role) VALUES('recycle-admin','unused','ADMIN')");
        classroom=db.insert("INSERT INTO classes(class_name,grade,academic_year,created_at,updated_at) VALUES('回收测试班','高一','test',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        student=db.insert("INSERT INTO students(student_no,name,class_id,status,created_at,updated_at) VALUES('26','张三',?,'在读',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",classroom);
        long exam=db.insert("INSERT INTO exams(name,class_id,exam_date) VALUES('测试考试',?,'2026-10-21')",classroom);
        db.insert("INSERT INTO scores(student_id,exam_id,subject,score,full_score,status) VALUES(?,?,'数学',85,100,'RECORDED')",student,exam);
        long assignment=db.insert("INSERT INTO assignments(name,class_id,subject,assigned_date,deadline) VALUES('测试作业',?,'数学','2026-10-20','2026-10-21 20:00:00')",classroom);
        db.insert("INSERT INTO assignment_records(assignment_id,student_id,status,included) VALUES(?,?,'PENDING',true)",assignment,student);
        attendance=db.insert("INSERT INTO attendance(student_id,type,occurred_date,recorder) VALUES(?,'LATE','2026-10-21','recycle-admin')",student);
        db.insert("INSERT INTO praises(student_id,praise_date,reason,recorder) VALUES(?,'2026-10-21','帮助同学','recycle-admin')",student);
    }
    long recycleId(String type){return ((Number)db.one("SELECT id FROM recycle_items WHERE entity_type=? AND restored_at IS NULL",type).get("id")).longValue();}
    @Test @WithMockUser(username="recycle-admin",roles="ADMIN") void classCascadeRestoresAllTypesAndOriginalIdentifiers(){
        assertThat(recycle.previewDelete("classes",classroom).get("total")).isEqualTo(8);
        assertThat(recycle.delete("classes",classroom).get("deleted")).isEqualTo(8);
        assertThat(db.query("SELECT id FROM students WHERE id=?",student)).isEmpty();
        var classified=recycle.list("students","26",0,20);assertThat(((Number)classified.get("total")).intValue()).isEqualTo(1);assertThat((List<?>)classified.get("types")).hasSize(8);
        long id=recycleId("classes");assertThat(recycle.restore(id,true).get("restored")).isEqualTo(8);
        assertThat(db.one("SELECT student_no FROM students WHERE id=?",student).get("studentNo")).isEqualTo("26");
        assertThat(db.one("SELECT id FROM attendance WHERE id=?",attendance).get("id")).isEqualTo(attendance);
        assertThat(((Number)recycle.list(null,"",0,20).get("total")).intValue()).isZero();
        assertThat(recycle.restore(id,true).get("restored")).isEqualTo(0);
    }
    @Test @WithMockUser(username="recycle-admin",roles="ADMIN") void existingDeleteEndpointUsesRecycleBinAndSingleRestore()throws Exception{
        mvc.perform(delete("/api/attendance/"+attendance)).andExpect(status().isOk());
        assertThat(db.query("SELECT id FROM attendance WHERE id=?",attendance)).isEmpty();
        mvc.perform(post("/api/recycle-bin/"+recycleId("attendance")+"/restore")).andExpect(status().isOk()).andExpect(jsonPath("$.data.restored").value(1));
        assertThat(db.query("SELECT id FROM attendance WHERE id=?",attendance)).hasSize(1);
    }
    @Test @WithMockUser(username="recycle-admin",roles="ADMIN") void aiPreviewRequiresConfirmationAndRejectsStaleDeletion()throws Exception{
        var args=json.valueToTree(Map.of("kind","recycle_delete","entityType","classes","recordId",classroom));
        var operation=writes.prepare(args);assertThat(db.query("SELECT id FROM students WHERE id=?",student)).hasSize(1);
        db.update("UPDATE students SET name='更名' WHERE id=?",student);
        assertThat(writes.confirm(operation.get("id").toString()).get("status")).isEqualTo("FAILED");
        assertThat(db.query("SELECT id FROM students WHERE id=?",student)).hasSize(1);
        var fresh=writes.prepare(args);assertThat(writes.confirm(fresh.get("id").toString()).get("status")).isEqualTo("SUCCEEDED");
        assertThat(writes.confirm(fresh.get("id").toString()).get("status")).isEqualTo("SUCCEEDED");
        var restore=writes.prepare(json.valueToTree(Map.of("kind","recycle_restore","recycleId",recycleId("classes"),"restoreBatch",true)));
        assertThat(writes.confirm(restore.get("id").toString())).containsEntry("status","SUCCEEDED");
    }
    @Test @WithMockUser(username="recycle-admin",roles="ADMIN") void restoreConflictDoesNotOverwriteExistingStudent()throws Exception{
        recycle.delete("students",student);
        db.insert("INSERT INTO students(student_no,name,class_id,status,created_at,updated_at) VALUES('26','新学生',?,'在读',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",classroom);
        assertThatThrownBy(()->recycle.restore(recycleId("students"),true)).hasMessageContaining("冲突");
        assertThat(db.one("SELECT name FROM students WHERE student_no='26'").get("name")).isEqualTo("新学生");
    }
    @Test @WithMockUser(roles="TEACHER") void teacherCannotDeleteRestoreOrReadRecycleBin()throws Exception{
        mvc.perform(get("/api/recycle-bin")).andExpect(status().isForbidden());
        mvc.perform(post("/api/recycle-bin/1/restore")).andExpect(status().isForbidden());
        assertThatThrownBy(()->recycle.delete("classes",classroom)).isInstanceOfAny(org.springframework.security.access.AccessDeniedException.class,org.springframework.security.core.AuthenticationException.class);
    }
}
