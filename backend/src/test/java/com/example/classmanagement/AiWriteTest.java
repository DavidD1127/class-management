package com.example.classmanagement;

import com.example.classmanagement.ai.*;
import com.example.classmanagement.business.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 使用真实事务与SQL，不在测试方法外包事务；模型协议单独模拟。 */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @WithMockUser(username="ai-writer",roles="TEACHER")
class AiWriteTest {
    @Autowired WriteTools writes;@Autowired BusinessRepository db;@Autowired ObjectMapper json;@Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;
    @MockBean DeepSeekClient client;
    long c,s,s2,e,a,u;
    @BeforeEach void setup()throws Exception{
        try(var conn=jdbc.getDataSource().getConnection()){String url=conn.getMetaData().getURL();if(!url.startsWith("jdbc:h2:mem:")&&!url.matches("jdbc:mysql://[^/]+/phase2_test(?:[?].*)?"))throw new IllegalStateException("仅允许独立测试库");}
        u=db.insert("INSERT INTO users(username,password,role) VALUES('ai-writer','test-only-not-login','TEACHER')");
        c=db.insert("INSERT INTO classes(class_name,grade,academic_year,created_at,updated_at) VALUES('AI写入测试班','测试','test',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        s=student("WRITE001","虚构甲");s2=student("WRITE002","虚构乙");
        e=db.insert("INSERT INTO exams(name,class_id,exam_date) VALUES('写入考试',?,'2026-10-20')",c);
        a=db.insert("INSERT INTO assignments(name,class_id,subject,assigned_date,deadline) VALUES('写入作业',?,'数学','2026-10-20','2026-10-20 20:00:00')",c);
        db.insert("INSERT INTO assignment_records(assignment_id,student_id,status) VALUES(?,?,'INCOMPLETE')",a,s);
    }
    long student(String no,String name){return db.insert("INSERT INTO students(student_no,name,class_id,status,created_at,updated_at) VALUES(?,?,?,'在读',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",no,name,c);}
    @AfterEach void cleanup(){
        db.update("DELETE FROM ai_operations WHERE user_id=?",u);db.update("DELETE FROM assignment_records WHERE assignment_id=?",a);db.update("DELETE FROM assignments WHERE id=?",a);
        db.update("DELETE FROM attendance WHERE student_id IN (?,?)",s,s2);db.update("DELETE FROM praises WHERE student_id IN (?,?)",s,s2);db.update("DELETE FROM scores WHERE exam_id=?",e);db.update("DELETE FROM exams WHERE id=?",e);db.update("DELETE FROM students WHERE class_id=?",c);db.update("DELETE FROM classes WHERE id=?",c);db.update("DELETE FROM users WHERE id=?",u);
    }
    String prepare(Map<String,Object> input){return writes.prepare(json.valueToTree(input)).get("id").toString();}
    Map<String,Object> attendance(){return new LinkedHashMap<>(Map.of("kind","attendance_add","studentNo","WRITE001","date","今天","type","LATE","lateMinutes",10));}
    @Test void attendanceConfirmedIdempotentAndEdit(){
        String op=prepare(attendance());assertThat(db.query("SELECT * FROM attendance WHERE student_id=?",s)).isEmpty();
        assertThat(writes.confirm(op)).containsEntry("status","SUCCEEDED");assertThat(writes.confirm(op)).containsEntry("status","SUCCEEDED");
        var record=db.one("SELECT * FROM attendance WHERE student_id=?",s);assertThat(record.get("remark")).isEqualTo("迟到10分钟");assertThat(record.get("recorder")).isEqualTo("ai-writer");
        assertThat(record.get("occurredDate")).isEqualTo(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString());
        String edit=prepare(Map.of("kind","attendance_edit","studentNo","WRITE001","recordId",record.get("id"),"remark","早自习迟到"));
        assertThat(writes.confirm(edit)).containsEntry("status","SUCCEEDED");assertThat(db.one("SELECT * FROM attendance WHERE id=?",record.get("id")).get("remark")).isEqualTo("早自习迟到");
        assertThat(writes.view(edit).get("preview").toString()).contains("before","迟到10分钟");
        String duplicate=prepare(attendance());assertThat(writes.view(duplicate).get("preview").toString()).contains("duplicates","早自习迟到");writes.cancel(duplicate);assertThat(writes.confirm(duplicate)).containsEntry("status","CANCELLED");
        assertThat(db.query("SELECT * FROM attendance WHERE student_id=?",s)).hasSize(1);
    }
    @Test void identityDateAndUnknownFieldsRejected(){
        var input=attendance();input.remove("date");assertThatThrownBy(()->prepare(input)).hasMessageContaining("日期");
        input.put("date","2026-02-30");assertThatThrownBy(()->prepare(input)).hasMessageContaining("日期");
        input.put("date","今天");input.put("studentNo","UNKNOWN");assertThatThrownBy(()->prepare(input)).hasMessageContaining("不存在");
        db.update("UPDATE students SET name='虚构甲' WHERE id=?",s2);input.remove("studentNo");input.put("name","虚构甲");assertThatThrownBy(()->prepare(input)).hasMessageContaining("同名");
        input.put("sql","DELETE FROM users");assertThatThrownBy(()->prepare(input)).hasMessageContaining("不支持");
        assertThatThrownBy(()->prepare(Map.of("kind","execute_sql"))).hasMessageContaining("未注册");
    }
    @Test void assignmentUpdateAndStaleSnapshot(){
        String op=prepare(Map.of("kind","assignment_state","studentNo","WRITE001","assignmentId",a,"status","COMPLETED","submittedAt","2026-10-20T19:00:00"));
        assertThat(writes.confirm(op)).containsEntry("status","SUCCEEDED");assertThat(db.one("SELECT * FROM assignment_records WHERE assignment_id=?",a).get("status")).isEqualTo("COMPLETED");
        String stale=prepare(Map.of("kind","assignment_state","studentNo","WRITE001","assignmentId",a,"status","INCOMPLETE"));db.update("UPDATE assignment_records SET remark='手工更新' WHERE assignment_id=?",a);
        assertThat(writes.confirm(stale)).containsEntry("status","FAILED");assertThat(db.one("SELECT * FROM assignment_records WHERE assignment_id=?",a).get("status")).isEqualTo("COMPLETED");
        assertThatThrownBy(()->prepare(Map.of("kind","assignment_state","studentNo","WRITE001","assignmentId",a,"status","COMPLETED"))).hasMessageContaining("实际提交时间");
    }
    @Test void praiseActorDuplicatesAndEdit(){
        String op=prepare(Map.of("kind","praise_add","studentNo","WRITE001","date","昨天","reason","帮助同学"));assertThat(writes.confirm(op)).containsEntry("status","SUCCEEDED");
        var r=db.one("SELECT * FROM praises WHERE student_id=?",s);assertThat(r.get("recorder")).isEqualTo("ai-writer");
        String edit=prepare(Map.of("kind","praise_edit","studentNo","WRITE001","recordId",r.get("id"),"reason","主动帮助同学"));assertThat(writes.confirm(edit)).containsEntry("status","SUCCEEDED");
        assertThat(db.one("SELECT * FROM praises WHERE id=?",r.get("id")).get("reason")).isEqualTo("主动帮助同学");
        String duplicate=prepare(Map.of("kind","praise_add","studentNo","WRITE001","date","昨天","reason","主动帮助同学"));assertThat(writes.view(duplicate).get("preview").toString()).contains("duplicates","主动帮助同学");
    }
    Map<String,Object> batch(List<Map<String,Object>> rows){return Map.of("kind","scores_add","examId",e,"subject","数学","fullScore",100,"rows",rows);}
    @Test void scorePreviewValidationSkipAndNoOverwrite(){
        String op=prepare(batch(List.of(Map.of("studentNo","WRITE001","score",92),Map.of("studentNo","WRITE002","score",101),Map.of("name","不存在","score",80))));
        assertThat(db.query("SELECT * FROM scores WHERE exam_id=?",e)).isEmpty();assertThat(writes.view(op).get("preview").toString()).contains("超过满分","学生不存在");
        assertThat(writes.confirm(op)).containsEntry("status","SUCCEEDED");assertThat(writes.view(op).get("result").toString()).contains("\"added\":1","\"skipped\":2");writes.confirm(op);
        assertThat(db.query("SELECT * FROM scores WHERE exam_id=?",e)).hasSize(1);
        String again=prepare(batch(List.of(Map.of("studentNo","WRITE001","score",20))));assertThat(writes.view(again).get("preview").toString()).contains("已有成绩");writes.confirm(again);
        assertThat(db.one("SELECT * FROM scores WHERE exam_id=?",e).get("score").toString()).isEqualTo("92.00");
        String dup=prepare(batch(List.of(Map.of("studentNo","WRITE002","score",80),Map.of("studentNo","WRITE002","score",90))));assertThat(writes.view(dup).get("preview").toString()).contains("全部重复行跳过");
    }
    @Test void batchDatabaseFailureRollsBackEveryRow(){
        String op=prepare(batch(List.of(Map.of("studentNo","WRITE001","score",80),Map.of("studentNo","WRITE002","score",90))));
        jdbc.execute("ALTER TABLE scores ADD CONSTRAINT ai_write_test_failure CHECK (score<>90)");
        try{assertThat(writes.confirm(op)).containsEntry("status","FAILED");assertThat(db.query("SELECT * FROM scores WHERE exam_id=?",e)).isEmpty();assertThat(writes.view(op).get("failure").toString()).contains("回滚");}
        finally{try(var conn=jdbc.getDataSource().getConnection()){jdbc.execute(conn.getMetaData().getURL().startsWith("jdbc:mysql:")?"ALTER TABLE scores DROP CHECK ai_write_test_failure":"ALTER TABLE scores DROP CONSTRAINT ai_write_test_failure");}catch(Exception ex){throw new RuntimeException(ex);}}
    }
    @Test void endpointAuthOwnershipAndExpiry()throws Exception{
        String op=prepare(attendance());var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        mvc.perform(post("/api/ai/operations/"+op+"/confirm").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ai/operations/"+op+"/confirm").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("viewer").roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/ai/operations/"+op).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("other").roles("TEACHER"))).andExpect(status().isNotFound());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        db.update("UPDATE ai_operations SET expires_at='2000-01-01 00:00:00' WHERE id=?",op);assertThat(writes.confirm(op)).containsEntry("status","FAILED");assertThat(db.query("SELECT * FROM attendance WHERE student_id=?",s)).isEmpty();
    }
    @Test void simultaneousConfirmationCommitsOnce()throws Exception{
        String op=prepare(attendance());var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var start=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<Map<String,Object>> task=()->{org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);try{start.await();return writes.confirm(op);}finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}};
        try{var one=pool.submit(task);var two=pool.submit(task);start.countDown();assertThat(one.get(20,java.util.concurrent.TimeUnit.SECONDS)).containsEntry("status","SUCCEEDED");assertThat(two.get(20,java.util.concurrent.TimeUnit.SECONDS)).containsEntry("status","SUCCEEDED");assertThat(db.query("SELECT * FROM attendance WHERE student_id=?",s)).hasSize(1);}finally{pool.shutdownNow();}
    }
    @Test void missingAndMismatchedTargetsRejected(){
        assertThatThrownBy(()->prepare(Map.of("kind","attendance_edit","studentNo","WRITE001","remark","更正"))).hasMessageContaining("原始记录");
        long foreignRecord=db.insert("INSERT INTO attendance(student_id,type,occurred_date,recorder) VALUES(?,'LATE','2026-10-20','test')",s2);
        assertThatThrownBy(()->prepare(Map.of("kind","attendance_edit","studentNo","WRITE001","recordId",foreignRecord,"remark","更正"))).hasMessageContaining("不存在");
        assertThatThrownBy(()->prepare(Map.of("kind","assignment_state","studentNo","WRITE001","status","INCOMPLETE"))).hasMessageContaining("作业");
        assertThatThrownBy(()->prepare(Map.of("kind","scores_add","examId",e,"subject","数学","rows",List.of(Map.of("studentNo","WRITE001","score",80))))).hasMessageContaining("满分");
        assertThatThrownBy(()->prepare(Map.of("kind","scores_add","subject","数学","fullScore",100,"rows",List.of(Map.of("studentNo","WRITE001","score",80))))).hasMessageContaining("考试");
        var malformed=attendance();malformed.put("time","25:00");assertThatThrownBy(()->prepare(malformed)).hasMessageContaining("发生时间");
        assertThat(db.query("SELECT * FROM ai_operations WHERE user_id=?",u)).isEmpty();
    }
    @Test void naturalLanguageToolCallProducesPreviewOnly()throws Exception{
        String session=json.readTree(mvc.perform(post("/api/ai/sessions")).andReturn().getResponse().getContentAsString()).at("/data/sessionId").asText();
        var reply=json.valueToTree(Map.of("tool_calls",List.of(Map.of("id","w1","type","function","function",Map.of("name","prepare_school_write","arguments",json.writeValueAsString(attendance()))))));
        when(client.chat(anyList(),anyList(),any())).thenReturn(reply);
        mvc.perform(post("/api/ai/chat").contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",session,"message","WRITE001今天迟到10分钟")))).andExpect(status().isOk()).andExpect(jsonPath("$.data.operation.status").value("PENDING"));
        assertThat(db.query("SELECT * FROM attendance WHERE student_id=?",s)).isEmpty();assertThat(db.query("SELECT * FROM ai_operations WHERE user_id=?",u)).hasSize(1);
    }
}
