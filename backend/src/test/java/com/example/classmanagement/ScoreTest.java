package com.example.classmanagement;

import com.example.classmanagement.business.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import java.math.BigDecimal;
import java.time.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional @WithMockUser(username="phase3-admin",roles="ADMIN")
class ScoreTest {
    @Autowired ScoreService scores;
    @Autowired ScoreImportService imports;
    @Autowired BusinessRepository db;
    @Autowired JdbcTemplate jdbc;
    long c,s1,s2,exam;
    @BeforeEach void setup() {
        c=db.insert("INSERT INTO classes(class_name,grade,academic_year,created_at,updated_at) VALUES('三阶段测试班','高一','test',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        s1=student("P3001");s2=student("P3002");
        exam=BusinessRules.id(scores.saveExam(null,new ScoreService.ExamInput("月考",c,LocalDate.of(2026,10,1))).get("id"));
    }
    long student(String no){return db.insert("INSERT INTO students(student_no,name,class_id,status,created_at,updated_at) VALUES(?,'虚构学生',?,'在读',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",no,c);}
    ScoreService.ScoreInput input(long student,String score,String full,String status) {return new ScoreService.ScoreInput(student,exam,"数学",score==null?null:new BigDecimal(score),new BigDecimal(full),status,"");}
    @Test void validatesAndPersistsAndSeparatesAbsence() {
        for(String value:new String[]{"-1","101"})assertThatThrownBy(()->scores.saveScore(null,input(s1,value,"100","RECORDED"))).hasMessageContaining("成绩");
        assertThatThrownBy(()->scores.saveScore(null,input(s1,null,"100","RECORDED"))).hasMessageContaining("成绩");
        assertThatThrownBy(()->scores.saveScore(null,input(s1,"1","0","RECORDED"))).hasMessageContaining("满分");
        long id=BusinessRules.id(scores.saveScore(null,input(s1,"0","100","RECORDED")).get("id"));
        scores.saveScore(null,input(s2,null,"100","ABSENT"));
        assertThatThrownBy(()->scores.saveScore(null,input(s1,"50","100","RECORDED"))).hasMessageContaining("已有成绩");
        scores.saveScore(id,input(s1,"90","100","RECORDED"));
        assertThat(jdbc.queryForObject("SELECT score FROM scores WHERE id=?",BigDecimal.class,id)).isEqualByComparingTo("90");
        assertThat(scores.scores(c,exam,s1,"数学")).hasSize(1);
        assertThat(scores.statistics(c,exam,"数学").get(0)).containsEntry("recorded",1).containsEntry("absent",1);
        assertThatThrownBy(()->scores.saveScore(null,input(-1,"1","100","RECORDED"))).hasMessageContaining("不存在");
    }
    @Test void percentageRankingStatisticsAndHistory() {
        scores.saveScore(null,input(s1,"80","100","RECORDED"));scores.saveScore(null,input(s2,"90","150","RECORDED"));
        var stat=scores.statistics(c,exam,"数学").get(0);
        assertThat((BigDecimal)stat.get("averagePercentage")).isEqualByComparingTo("70");
        assertThat((BigDecimal)stat.get("averageScore")).isEqualByComparingTo("85");
        assertThat((BigDecimal)stat.get("highestScore")).isEqualByComparingTo("90");
        assertThat((BigDecimal)stat.get("lowestScore")).isEqualByComparingTo("80");
        assertThat((BigDecimal)stat.get("passRate")).isEqualByComparingTo("100");
        var ranks=(java.util.List<java.util.Map<String,Object>>)stat.get("ranking");assertThat(BusinessRules.id(ranks.get(0).get("studentId"))).isEqualTo(s1);
        exam=BusinessRules.id(scores.saveExam(null,new ScoreService.ExamInput("期中",c,LocalDate.of(2026,11,1))).get("id"));
        scores.saveScore(null,input(s1,"95","100","RECORDED"));assertThat(scores.scores(c,null,s1,"数学")).hasSize(2);
    }
    @Test void summaryGroupsByExamAndStudentAndOnlyTotalsCompleteResults() {
        long s3=student("P3003");
        scores.saveScore(null,input(s1,"0","100","RECORDED"));
        scores.saveScore(null,new ScoreService.ScoreInput(s1,exam,"语文",new BigDecimal("80.25"),new BigDecimal("150"),"RECORDED",""));
        scores.saveScore(null,input(s2,"90","100","RECORDED"));
        scores.saveScore(null,input(s3,null,"100","ABSENT"));
        scores.saveScore(null,new ScoreService.ScoreInput(s3,exam,"语文",BigDecimal.TEN,new BigDecimal("150"),"RECORDED",""));

        var rows=scores.summary(c,exam,null,null);
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).containsEntry("studentId",s1);
        assertThat((BigDecimal)rows.get(0).get("totalScore")).isEqualByComparingTo("80.25");
        assertThat((BigDecimal)rows.get(0).get("totalFullScore")).isEqualByComparingTo("250");
        assertThat(scores.summary(c,exam,s2,null).get(0)).containsEntry("subjects",rows.get(0).get("subjects")).doesNotContainKey("totalScore");
        assertThat(rows.get(2)).doesNotContainKey("totalScore");
        assertThat(scores.summary(c,exam,s1,"数学").get(0)).doesNotContainKey("totalScore");

        exam=BusinessRules.id(scores.saveExam(null,new ScoreService.ExamInput("期中",c,LocalDate.of(2026,11,1))).get("id"));
        scores.saveScore(null,input(s1,"95","100","RECORDED"));
        assertThat(scores.summary(c,null,s1,null)).hasSize(2);
    }
    MockMultipartFile csv(String rows){return new MockMultipartFile("file","scores.csv","text/csv",("学号,姓名,成绩,状态,备注\n"+rows).getBytes(StandardCharsets.UTF_8));}
    @Test void importsPreviewConfirmAndExplicitOverwrite() {
        var p=imports.preview(csv("P3001,虚构学生,70,,\nP3002,虚构学生,-1,,\n"),exam,"数学",new BigDecimal("100"),"phase3-admin");
        assertThat(p.valid()).isEqualTo(1);assertThat(scores.scores(c,exam,null,null)).isEmpty();
        assertThat(imports.confirm(p.previewId(),false,"phase3-admin")).containsEntry("success",1).containsEntry("skipped",1);
        p=imports.preview(csv("P3001,虚构学生,99,,\n"),exam,"数学",new BigDecimal("100"),"phase3-admin");
        assertThat(p.existing()).isEqualTo(1);assertThat(imports.confirm(p.previewId(),false,"phase3-admin")).containsEntry("success",0);
        p=imports.preview(csv("P3001,虚构学生,99,,\n"),exam,"数学",new BigDecimal("100"),"phase3-admin");
        assertThat(imports.confirm(p.previewId(),true,"phase3-admin")).containsEntry("success",1);
        assertThat((BigDecimal)scores.scores(c,exam,s1,null).get(0).get("score")).isEqualByComparingTo("99");
        var token=p.previewId();assertThatThrownBy(()->imports.confirm(token,true,"phase3-admin")).hasMessageContaining("预览");
    }
    @Test void tiesUseCompetitionRanksAndRejectWrongClass() {
        long s3=student("P3003");
        scores.saveScore(null,input(s1,"80","100","RECORDED"));scores.saveScore(null,input(s2,"120","150","RECORDED"));scores.saveScore(null,input(s3,"70","100","RECORDED"));
        var ranks=(java.util.List<java.util.Map<String,Object>>)scores.statistics(c,exam,"数学").get(0).get("ranking");
        assertThat(ranks.stream().map(r->r.get("rank"))).containsExactly(1,1,3);
        long other=db.insert("INSERT INTO classes(class_name,grade,academic_year,created_at,updated_at) VALUES('其他班级','高一','test',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        db.update("UPDATE students SET class_id=? WHERE id=?",other,s3);
        assertThatThrownBy(()->scores.saveScore(null,new ScoreService.ScoreInput(s3,exam,"语文",BigDecimal.ONE,BigDecimal.TEN,"RECORDED",""))).hasMessageContaining("不属于");
    }
    @Test void leadersUseCompleteSubjectTotalsAndLatestPopulatedExam(){
        long s3=student("P3003");
        for(String subject:new String[]{"语文","数学","英语"}){
            scores.saveScore(null,new ScoreService.ScoreInput(s1,exam,subject,new BigDecimal("80"),new BigDecimal("100"),"RECORDED",""));
            scores.saveScore(null,new ScoreService.ScoreInput(s2,exam,subject,new BigDecimal("80"),new BigDecimal("100"),"RECORDED",""));
            scores.saveScore(null,new ScoreService.ScoreInput(s3,exam,subject,subject.equals("英语")?null:new BigDecimal("99"),new BigDecimal("100"),subject.equals("英语")?"ABSENT":"RECORDED",""));
        }
        scores.saveExam(null,new ScoreService.ExamInput("还没录分的新考试",c,LocalDate.of(2026,10,20)));
        var total=scores.leaders(c,null,null);assertThat(total).containsEntry("examName","月考").containsEntry("recorded",2).containsEntry("absent",1);
        var ranking=(java.util.List<java.util.Map<String,Object>>)total.get("ranking");assertThat(ranking).hasSize(2);assertThat(ranking.stream().map(r->r.get("rank"))).containsExactly(1,1);assertThat((BigDecimal)ranking.get(0).get("score")).isEqualByComparingTo("240");
        var single=scores.leaders(c,null,"数学");assertThat(((java.util.List<java.util.Map<String,Object>>)single.get("ranking")).get(0)).containsEntry("studentId",s3);
    }
    @Test void leadersRejectInconsistentFullScores(){
        scores.saveScore(null,input(s1,"80","100","RECORDED"));scores.saveScore(null,input(s2,"120","150","RECORDED"));
        assertThatThrownBy(()->scores.leaders(c,exam,"数学")).hasMessageContaining("满分不一样");
    }
}
