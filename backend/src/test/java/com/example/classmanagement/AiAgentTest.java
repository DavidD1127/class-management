package com.example.classmanagement;

import com.example.classmanagement.ai.*;
import com.example.classmanagement.business.BusinessRepository;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AiAgentTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired BusinessRepository db;
    @Autowired com.example.classmanagement.security.JwtService jwt;
    @MockBean DeepSeekClient client;
    long classroom;
    final List<String> createdSessions=new ArrayList<>();
    @BeforeEach void setup(){
        db.insert("INSERT INTO users(username,password,role) VALUES('agent-admin','unused','ADMIN')");
        classroom=db.insert("INSERT INTO classes(class_name,grade,academic_year,created_at,updated_at) VALUES('高一（1）班','高一','test',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        db.insert("INSERT INTO students(student_no,name,class_id,status,created_at,updated_at) VALUES('AG001','张三',?,'在读',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",classroom);
    }
    String session()throws Exception{String id=json.readTree(mvc.perform(post("/api/ai/sessions")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("sessionId").asText();createdSessions.add(id);return id;}
    @AfterEach void clearSessions()throws Exception{for(String id:createdSessions)mvc.perform(delete("/api/ai/sessions/"+id)).andExpect(status().isOk());}
    JsonNode tool(String name,Object args)throws Exception{return json.valueToTree(Map.of("tool_calls",List.of(Map.of("id","call_1","type","function","function",Map.of("name",name,"arguments",json.writeValueAsString(args))))));}
    Map<String,Object> attendance(){return Map.of("kind","attendance_add","classId",classroom,"name","张三","date","今天","type","LATE");}
    @Test @WithMockUser(username="agent-admin",roles="ADMIN") void explicitAttendanceSavesOnceAndDuplicateRequiresReview()throws Exception{
        when(client.chat(anyList(),anyList(),any())).thenReturn(tool("prepare_school_write",attendance()));
        String id=session();var request=Map.of("sessionId",id,"message","今天一班张三迟到了，给我记上","autoRecord",true,"style","warm");
        mvc.perform(post("/api/ai/chat").contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isOk()).andExpect(jsonPath("$.data.operation.status").value("SUCCEEDED")).andExpect(jsonPath("$.data.answer").value("记好了，张三的考勤记录已保存。"));
        mvc.perform(post("/api/ai/chat").contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isOk()).andExpect(jsonPath("$.data.operation.status").value("PENDING"));
        assertThat(((Number)db.one("SELECT COUNT(*) AS total FROM attendance").get("total")).intValue()).isEqualTo(1);
    }
    @Test @WithMockUser(username="agent-admin",roles="ADMIN") void negativeCommandNeverAutoSaves()throws Exception{
        when(client.chat(anyList(),anyList(),any())).thenReturn(tool("prepare_school_write",attendance()));
        mvc.perform(post("/api/ai/chat").contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",session(),"message","今天一班张三迟到了，不要记录","autoRecord",true))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.operation.status").value("PENDING"));
        assertThat(((Number)db.one("SELECT COUNT(*) AS total FROM attendance").get("total")).intValue()).isZero();
    }
    @Test @WithMockUser(username="agent-admin",roles="ADMIN") void resolvedStudentNumberCanSaveWhenUserProvidedOnlyName()throws Exception{
        when(client.chat(anyList(),anyList(),any())).thenReturn(tool("prepare_school_write",Map.of("kind","attendance_add","studentNo","AG001","date","今天","type","LATE")));
        mvc.perform(post("/api/ai/chat").contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",session(),"message","今天一班张三迟到了，给我记上","autoRecord",true))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.operation.status").value("SUCCEEDED"));
        assertThat(((Number)db.one("SELECT COUNT(*) AS total FROM attendance").get("total")).intValue()).isEqualTo(1);
    }
    @Test @WithMockUser(username="agent-admin",roles="ADMIN") void omittedClassIdCannotAutoSaveForWrongOrUnknownClass()throws Exception{
        db.insert("INSERT INTO classes(class_name,grade,academic_year,created_at,updated_at) VALUES('高一（2）班','高一','test',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        when(client.chat(anyList(),anyList(),any())).thenReturn(tool("prepare_school_write",Map.of("kind","attendance_add","studentNo","AG001","date","今天","type","LATE")));
        for(String classroomName:List.of("二班","九班"))mvc.perform(post("/api/ai/chat").contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",session(),"message","今天"+classroomName+"张三迟到了，给我记上","autoRecord",true))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.operation.status").value("PENDING"));
        assertThat(((Number)db.one("SELECT COUNT(*) AS total FROM attendance").get("total")).intValue()).isZero();
    }
    @Test @WithMockUser(username="agent-admin",roles="ADMIN") void personaReachesModelAndLongPersonaIsRejected()throws Exception{
        when(client.chat(anyList(),anyList(),any())).thenAnswer(call->{assertThat(call.getArgument(0).toString()).contains("温和耐心","称呼我为李老师");return json.valueToTree(Map.of("content","请补充要查询的内容"));});
        String id=session();mvc.perform(post("/api/ai/chat").contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",id,"message","你好","style","warm","persona","称呼我为李老师")))).andExpect(status().isOk());
        mvc.perform(post("/api/ai/chat").contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",id,"message","你好","persona","长".repeat(501))))).andExpect(status().isBadRequest());
        verify(client,times(1)).chat(anyList(),anyList(),any());
    }
    @Test @WithMockUser(username="agent-admin",roles="ADMIN") void ordinaryChatKeepsPersonaAndContextWithoutDatabaseOperations()throws Exception{
        String id=session();
        doAnswer(call->{
            assertThat(call.getArgument(1).toString()).contains("reply_to_user");
            assertThat(call.getArgument(0).toString()).contains("温和耐心","称呼我为李老师");
            return tool("reply_to_user",Map.of("answer","李老师，辛苦了。想聊聊今天发生了什么吗？"));
        }).when(client).chat(anyList(),anyList(),any());
        mvc.perform(post("/api/ai/chat").contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",id,"message","今天有点累，陪我聊聊天","style","warm","persona","称呼我为李老师","autoRecord",true))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.answer").value("李老师，辛苦了。想聊聊今天发生了什么吗？")).andExpect(jsonPath("$.data.queryResults").isEmpty()).andExpect(jsonPath("$.data.operation").doesNotExist());
        doAnswer(call->{
            assertThat(call.getArgument(0).toString()).contains("今天有点累，陪我聊聊天","李老师，辛苦了。想聊聊今天发生了什么吗？","帮我写两句鼓励自己的话");
            return tool("reply_to_user",Map.of("answer","今天已经很努力了。允许自己休息，明天再慢慢来。"));
        }).when(client).chat(anyList(),anyList(),any());
        mvc.perform(post("/api/ai/chat").contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",id,"message","帮我写两句鼓励自己的话"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.answer").value("今天已经很努力了。允许自己休息，明天再慢慢来。")).andExpect(jsonPath("$.data.queryResults").isEmpty());
        verify(client,times(2)).chat(anyList(),anyList(),any());
        assertThat(db.query("SELECT id FROM attendance")).isEmpty();
        assertThat(db.query("SELECT id FROM students WHERE student_no='AG001'")).hasSize(1);
    }
    @Test @WithMockUser(username="agent-admin",roles="ADMIN") void ordinaryChatRejectsUnexpectedArguments()throws Exception{
        when(client.chat(anyList(),anyList(),any())).thenReturn(tool("reply_to_user",Map.of("answer","你好","sql","DELETE FROM students")));
        mvc.perform(post("/api/ai/chat").contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",session(),"message","你好"))))
            .andExpect(status().isBadRequest());
        assertThat(db.query("SELECT id FROM students WHERE student_no='AG001'")).hasSize(1);
    }
    @Test @WithMockUser(username="agent-admin",roles="ADMIN") void simpleHighestScoreIsLocalAndUsesPlainPoints()throws Exception{
        long student=((Number)db.one("SELECT id FROM students WHERE student_no='AG001'").get("id")).longValue();
        long exam=db.insert("INSERT INTO exams(name,class_id,exam_date) VALUES('10月月考',?,'2026-10-20')",classroom);
        for(String subject:List.of("语文","数学","英语"))db.insert("INSERT INTO scores(student_id,exam_id,subject,score,full_score,status) VALUES(?,?,?,90,100,'RECORDED')",student,exam,subject);
        String body=mvc.perform(post("/api/ai/chat").contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",session(),"message","一班谁最高分"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.queryResults[0].kind").value("score_leaders")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(json.readTree(body).path("data").path("answer").asText()).contains("张三","270分").doesNotContain("得分率","权重","请补充");verifyNoInteractions(client);
        mvc.perform(post("/api/ai/chat").contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",session(),"message","一班10月月考数学谁最高分"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.answer").value(org.hamcrest.Matchers.containsString("90分")));
        verifyNoInteractions(client);
        when(client.chat(anyList(),anyList(),any())).thenReturn(tool("reply_to_user",Map.of("answer","想查今天哪场考试？")));
        mvc.perform(post("/api/ai/chat").contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",session(),"message","今天一班谁最高分"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.answer").value("想查今天哪场考试？"));
        verify(client,times(1)).chat(anyList(),anyList(),any());
    }
    @Test @WithMockUser(username="agent-admin",roles="ADMIN") void simpleAttendanceWithTimeUsesLocalValidation()throws Exception{
        String body=mvc.perform(post("/api/ai/chat").contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",session(),"message","今天一班张三7:41:00迟到了，给我记上","autoRecord",true))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.operation.status").value("SUCCEEDED")).andReturn().getResponse().getContentAsString();
        assertThat(db.one("SELECT occurred_time FROM attendance").get("occurredTime")).isEqualTo("07:41");verifyNoInteractions(client);
    }
    @Test @WithMockUser(username="agent-admin",roles="ADMIN") void aiDeletionNeedsRealQueryAndOnlyCreatesPreview()throws Exception{
        var deletion=Map.of("kind","recycle_delete","entityType","classes","recordId",classroom);
        // 模型不听劝、反复提交同一次未经查询的删除：错误会回给它，但它重复同一调用，
        // 于是被去重机制拦下，最终报错且业务数据不动。
        when(client.chat(anyList(),anyList(),any())).thenReturn(tool("prepare_school_write",deletion));
        mvc.perform(post("/api/ai/chat").contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",session(),"message","删除一班所有数据"))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("重复请求")));
        assertThat(db.query("SELECT id FROM students WHERE student_no='AG001'")).hasSize(1);
        // 模型先查询拿到真实编号、再提交删除：这次应当成功生成待确认预览（仍未执行）。
        when(client.chat(anyList(),anyList(),any())).thenReturn(tool("query_school",Map.of("kind","classes")),tool("prepare_school_write",deletion));
        mvc.perform(post("/api/ai/chat").contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",session(),"message","删除一班所有数据","autoRecord",true))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.operation.kind").value("recycle_delete")).andExpect(jsonPath("$.data.operation.status").value("PENDING")).andExpect(jsonPath("$.data.operation.preview.total").value(2));
        assertThat(db.query("SELECT id FROM students WHERE student_no='AG001'")).hasSize(1);
    }
    @Test @WithMockUser(username="agent-admin",roles="ADMIN") void aiCanFindRecycleEntryAndPrepareRestoration()throws Exception{
        long student=((Number)db.one("SELECT id FROM students WHERE student_no='AG001'").get("id")).longValue();
        mvc.perform(delete("/api/students/"+student)).andExpect(status().isOk());
        long recycled=((Number)db.one("SELECT id FROM recycle_items WHERE entity_type='students' AND record_id=? AND restored_at IS NULL",student).get("id")).longValue();
        when(client.chat(anyList(),anyList(),any())).thenReturn(tool("query_recycle_bin",Map.of("type","students","q","AG001")),tool("prepare_school_write",Map.of("kind","recycle_restore","recycleId",recycled,"restoreBatch",true)));
        String body=mvc.perform(post("/api/ai/chat").contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",session(),"message","把AG001学生和关联数据恢复"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.operation.status").value("PENDING")).andReturn().getResponse().getContentAsString();
        String operation=json.readTree(body).path("data").path("operation").path("id").asText();
        mvc.perform(post("/api/ai/operations/"+operation+"/confirm")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
        assertThat(db.query("SELECT id FROM students WHERE student_no='AG001'")).hasSize(1);
    }
    @Test @WithMockUser(username="agent-admin",roles="ADMIN") void rosterPreviewReusesValidationAndOnlyValidRowsImport()throws Exception{
        var rows=List.of(Map.of("studentNo","AG002","name","李四","className","高一（1）班"),Map.of("studentNo","","name","缺少学号","className","高一（1）班"));
        when(client.chat(anyList(),anyList(),any())).thenReturn(tool("prepare_student_import",Map.of("rows",rows)));
        String body=mvc.perform(post("/api/ai/chat").contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",session(),"message","整理名单","attachment","AG002 李四 高一（1）班\n缺少学号 高一（1）班")))).andExpect(status().isOk()).andExpect(jsonPath("$.data.rosterPreview.valid").value(1)).andExpect(jsonPath("$.data.rosterPreview.errors").value(1)).andReturn().getResponse().getContentAsString();
        String preview=json.readTree(body).path("data").path("rosterPreview").path("previewId").asText();
        mvc.perform(post("/api/students/import/confirm").contentType("application/json").content(json.writeValueAsString(Map.of("previewId",preview)))).andExpect(status().isOk()).andExpect(jsonPath("$.data.success").value(1));
        assertThat(db.query("SELECT student_no FROM students WHERE student_no='AG002'")).hasSize(1);
    }
    @Test @WithMockUser(username="agent-admin",roles="ADMIN") void csvAliasesAndSelectedClassUseNoModel()throws Exception{
        var file=new MockMultipartFile("file","名单.csv","text/csv","学生姓名,学生编号\n王五,AG003\n".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/ai/attachments").file(file).param("classId",String.valueOf(classroom))).andExpect(status().isOk()).andExpect(jsonPath("$.data.rosterPreview.valid").value(1)).andExpect(jsonPath("$.data.rosterPreview.rows[0].name").value("王五"));
        verifyNoInteractions(client);
    }
    @Test @WithMockUser(username="agent-admin",roles="ADMIN") void rejectsDisguisedImage(){
        assertThatCode(()->mvc.perform(multipart("/api/ai/attachments").file(new MockMultipartFile("file","名单.png","image/png","not-an-image".getBytes(StandardCharsets.UTF_8)))).andExpect(status().isBadRequest())).doesNotThrowAnyException();
    }
    @Test void anonymousAttachmentIsRejected()throws Exception{mvc.perform(multipart("/api/ai/attachments").file(new MockMultipartFile("file","x.txt","text/plain",new byte[]{1}))).andExpect(status().isUnauthorized());}
    @Test @WithMockUser(username="agent-admin",roles="ADMIN") void streamDeliversProgressAndFinalReply()throws Exception{
        when(client.chat(anyList(),anyList(),any())).thenReturn(json.valueToTree(Map.of("content","请补充要查询的科目")));
        var pending=mvc.perform(post("/api/ai/chat/stream").contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",session(),"message","查成绩")))).andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(pending)).andExpect(status().isOk()).andExpect(result->assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("event:status","event:result","请补充要查询的科目"));
    }
    @Test void jwtStreamAuthenticatesAgainOnAsyncDispatch()throws Exception{
        String authorization="Bearer "+jwt.create("agent-admin");
        when(client.chat(anyList(),anyList(),any())).thenReturn(json.valueToTree(Map.of("content","请补充要查询的科目")));
        String id=json.readTree(mvc.perform(post("/api/ai/sessions").header("Authorization",authorization)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("sessionId").asText();
        try{
            var pending=mvc.perform(post("/api/ai/chat/stream").header("Authorization",authorization).contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",id,"message","查成绩")))).andExpect(request().asyncStarted()).andReturn();
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            mvc.perform(asyncDispatch(pending)).andExpect(status().isOk()).andExpect(result->assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("event:result"));
        }finally{mvc.perform(delete("/api/ai/sessions/"+id).header("Authorization",authorization)).andExpect(status().isOk());}
    }
}
