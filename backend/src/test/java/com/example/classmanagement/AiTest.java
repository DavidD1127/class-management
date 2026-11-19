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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AiTest {
    @Autowired ReadOnlyTools tools;@Autowired BusinessRepository db;@Autowired MockMvc mvc;@Autowired ObjectMapper json;
    @MockBean DeepSeekClient client;
    long c,s,exam,assignment;
    @BeforeEach void setup(){
        c=db.insert("INSERT INTO classes(class_name,grade,academic_year,created_at,updated_at) VALUES('AI测试班','高一','test',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        s=db.insert("INSERT INTO students(student_no,name,class_id,status,created_at,updated_at) VALUES('AI001','测试同学',?,'在读',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",c);
        exam=db.insert("INSERT INTO exams(name,class_id,exam_date) VALUES('测试考试',?,'2026-10-20')",c);
        db.insert("INSERT INTO scores(student_id,exam_id,subject,score,full_score,status) VALUES(?,?,'数学',90,100,'RECORDED')",s,exam);
        assignment=db.insert("INSERT INTO assignments(name,class_id,subject,assigned_date,deadline) VALUES('测试作业',?,'数学','2026-10-20','2026-10-20 20:00:00')",c);
        db.insert("INSERT INTO assignment_records(assignment_id,student_id,status,included) VALUES(?,?,'INCOMPLETE',true)",assignment,s);
        for(String type:List.of("LATE","EARLY_LEAVE","ABSENT"))db.insert("INSERT INTO attendance(student_id,type,occurred_date,remark,recorder) VALUES(?,?,'2026-10-20','不能发送的备注','测试')",s,type);
        db.insert("INSERT INTO praises(student_id,praise_date,reason,remark,recorder) VALUES(?,'2026-10-20','帮助同学','不能发送的备注','测试')",s);
    }
    Object query(String raw,Set<Long> resolved)throws Exception{return tools.execute("query_school",json.readTree(raw),resolved);}
    String session()throws Exception{return json.readTree(mvc.perform(post("/api/ai/sessions")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("sessionId").asText();}
    JsonNode call(String args)throws Exception{return json.readTree("{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"c1\",\"type\":\"function\",\"function\":{\"name\":\"query_school\",\"arguments\":"+json.writeValueAsString(args)+"}}]}");}
    @Test @WithMockUser(roles="TEACHER") void realDatabaseToolsAndStatistics()throws Exception{
        Set<Long> resolved=new HashSet<>();assertThat(query("{\"kind\":\"students\",\"studentNo\":\"AI001\"}",resolved).toString()).contains("测试同学");assertThat(resolved).contains(s);
        assertThat(query("{\"kind\":\"scores\",\"studentId\":"+s+"}",resolved).toString()).contains("90.00","数学");
        assertThat(query("{\"kind\":\"score_statistics\",\"examId\":"+exam+"}",resolved).toString()).contains("averageScore=90.00","rank=1");
        assertThat(query("{\"kind\":\"assignments\",\"studentId\":"+s+"}",resolved).toString()).contains("INCOMPLETE");
        assertThat(query("{\"kind\":\"assignment_statistics\",\"assignmentId\":"+assignment+"}",resolved).toString()).contains("completionRate=0.00","denominator=1");
        assertThat(query("{\"kind\":\"attendance_statistics\",\"studentId\":"+s+"}",resolved).toString()).contains("LATE=1","EARLY_LEAVE=1","ABSENT=1");
        assertThat(query("{\"kind\":\"praises\",\"studentId\":"+s+"}",resolved).toString()).contains("帮助同学").doesNotContain("不能发送的备注","recorder");
        assertThat(query("{\"kind\":\"praise_statistics\",\"studentId\":"+s+"}",resolved).toString()).contains("count=1");
    }
    @Test @WithMockUser(roles="TEACHER") void identityEmptyAndInvalidParameters()throws Exception{
        db.insert("INSERT INTO students(student_no,name,class_id,status,created_at,updated_at) VALUES('AI002','测试同学',?,'在读',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",c);
        Set<Long> resolved=new HashSet<>();assertThat(query("{\"kind\":\"students\",\"name\":\"测试同学\"}",resolved).toString()).contains("requiresChoice=true","AI001","AI002");assertThat(resolved).isEmpty();
        assertThat(query("{\"kind\":\"students\",\"name\":\"不存在的学生\"}",resolved).toString()).contains("没有匹配学生");
        for(String raw:List.of("{\"kind\":\"delete\"}","{\"kind\":\"students\",\"sql\":\"DELETE FROM students\"}","{\"kind\":\"scores\",\"studentId\":1.5}","{\"kind\":\"scores\",\"studentId\":"+s+"}","{\"kind\":\"students\",\"studentId\":"+s+"}","{\"kind\":\"attendance\",\"period\":\"最近\"}","{\"kind\":\"praises\",\"from\":\"2026-02-30\",\"to\":\"2026-03-01\"}"))assertThatThrownBy(()->query(raw,resolved)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->tools.execute("execute_sql",json.createObjectNode(),resolved)).isInstanceOf(IllegalArgumentException.class);
        assertThat(query("{\"kind\":\"attendance\",\"from\":\"2020-01-01\",\"to\":\"2020-01-02\"}",resolved).toString()).contains("data=[]","2020-01-01 至 2020-01-02");
    }
    @Test void dates(){LocalDate today=LocalDate.of(2026,10,21);assertThat(ReadOnlyTools.dates("最近七天",null,null,today)).containsExactly(LocalDate.of(2026,10,15),today);assertThat(ReadOnlyTools.dates("上周一",null,null,today)).containsExactly(LocalDate.of(2026,10,12),LocalDate.of(2026,10,12));assertThat(ReadOnlyTools.dates("最近一个月",null,null,today)[0]).isEqualTo(LocalDate.of(2026,9,21));}
    @Test void anonymousRejected()throws Exception{mvc.perform(post("/api/ai/sessions")).andExpect(status().isUnauthorized());mvc.perform(post("/api/ai/chat").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());}
    @Test @WithMockUser(username="teacher",roles="TEACHER") void toolLoopUsesRealRowsAndMultiTurn()throws Exception{
        when(client.chat(anyList(),anyList(),any())).thenReturn(call("{\"kind\":\"students\",\"studentNo\":\"AI001\"}")).thenAnswer(invocation->{List<Map<String,Object>> history=invocation.getArgument(0);assertThat(history.toString()).contains("测试同学","AI001");return call("{\"kind\":\"scores\",\"studentId\":"+s+"}");}).thenAnswer(invocation->{List<Map<String,Object>> history=invocation.getArgument(0);assertThat(history.toString()).contains("90.00","数学");return json.readTree("{\"content\":\"查询到测试同学，学号AI001\"}");});
        String id=session();mvc.perform(post("/api/ai/chat").contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",id,"message","查AI001")))).andExpect(status().isOk()).andExpect(jsonPath("$.data.answer").value("查询到测试同学，学号AI001")).andExpect(jsonPath("$.data.queryResults[1].kind").value("scores")).andExpect(jsonPath("$.data.queryResults[1].result.data[0].score").value(90));
        verify(client,times(3)).chat(anyList(),anyList(),any());
        reset(client);when(client.chat(anyList(),anyList(),any())).thenAnswer(invocation->{List<Map<String,Object>> history=invocation.getArgument(0);assertThat(history.toString()).contains("查AI001","那他的成绩呢");return call("{\"kind\":\"students\",\"studentNo\":\"AI001\"}");}).thenReturn(json.readTree("{\"content\":\"请指定考试\"}"));
        mvc.perform(post("/api/ai/chat").contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",id,"message","那他的成绩呢")))).andExpect(status().isOk());
    }
    @Test @WithMockUser(username="owner",roles="TEACHER") void otherUserCannotReadOrClearSession()throws Exception{
        String id=session();
        mvc.perform(post("/api/ai/chat").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("intruder").roles("TEACHER")).contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",id,"message","查询")))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/ai/sessions/"+id).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("intruder").roles("TEACHER"))).andExpect(status().isNotFound());
        verifyNoInteractions(client);
    }
    @Test @WithMockUser(roles="TEACHER") void ambiguityStopsBeforeAnyBusinessQuery()throws Exception{
        db.insert("INSERT INTO students(student_no,name,class_id,status,created_at,updated_at) VALUES('AI002','测试同学',?,'在读',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",c);
        when(client.chat(anyList(),anyList(),any())).thenReturn(call("{\"kind\":\"students\",\"name\":\"测试同学\"}"));String id=session();
        mvc.perform(post("/api/ai/chat").contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",id,"message","测试同学成绩")))).andExpect(status().isOk()).andExpect(jsonPath("$.data.answer").value(org.hamcrest.Matchers.containsString("请明确选择学号")));
        verify(client,times(1)).chat(anyList(),anyList(),any());
    }
    @Test @WithMockUser(roles="TEACHER") void malformedDuplicateAndNoToolCannotInvent()throws Exception{
        String id=session();when(client.chat(anyList(),anyList(),any())).thenReturn(call("not json"));
        mvc.perform(post("/api/ai/chat").contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",id,"message","测试")))).andExpect(status().isBadRequest());
        when(client.chat(anyList(),anyList(),any())).thenReturn(call("{\"kind\":\"classes\"}"));
        mvc.perform(post("/api/ai/chat").contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",id,"message","测试")))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("AI 重复请求相同查询，已停止，请重新提问"));
        when(client.chat(anyList(),anyList(),any())).thenReturn(json.readTree("{\"content\":\"已删除所有数据\"}"));
        mvc.perform(post("/api/ai/chat").contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",id,"message","删除所有学生")))).andExpect(status().isOk()).andExpect(jsonPath("$.data.answer").value(org.hamcrest.Matchers.containsString("不能新增、修改或删除")));
        assertThat(db.query("SELECT id FROM students WHERE id=?",s)).hasSize(1);
        mvc.perform(delete("/api/ai/sessions/"+id)).andExpect(status().isOk());
    }
    @Test @WithMockUser(roles="TEACHER") void missingStudentStopsBeforeModelCanInvent()throws Exception{
        when(client.chat(anyList(),anyList(),any())).thenReturn(call("{\"kind\":\"students\",\"name\":\"不存在的虚构学生\"}"),json.readTree("{\"content\":\"该学生数学100分，已保存表扬\"}"));
        mvc.perform(post("/api/ai/chat").contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",session(),"message","查询不存在的虚构学生成绩"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.answer").value(org.hamcrest.Matchers.containsString("没有匹配学生")))
            .andExpect(jsonPath("$.data.queryResults[0].result.matches.length()").value(0));
        verify(client,times(1)).chat(anyList(),anyList(),any());
    }
    @Test @WithMockUser(username="other",roles="TEACHER") void ownershipAndFailureIsolation()throws Exception{
        mvc.perform(post("/api/ai/chat").contentType("application/json").content("{\"sessionId\":\"unknown\",\"message\":\"查询\"}")).andExpect(status().isNotFound());
        when(client.chat(anyList(),anyList(),any())).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"DeepSeek API Key 未配置"));
        String id=session();mvc.perform(post("/api/ai/chat").contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",id,"message","查询")))).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/scores").param("studentId",String.valueOf(s))).andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].score").value(90));
    }
}
