package com.example.classmanagement;

import com.example.classmanagement.business.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.mock.web.MockMultipartFile;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** 不包裹测试级事务，以确认服务提交、失败回滚与一次性令牌的实际效果。 */
@SpringBootTest @ActiveProfiles("test") @WithMockUser(username="atomic-admin",roles="ADMIN")
class ScoreImportAtomicTest {
    @Autowired ScoreService scores; @Autowired ScoreImportService imports; @Autowired BusinessRepository db;
    long c,s1,s2,e;
    @BeforeEach void setup(){
        c=db.insert("INSERT INTO classes(class_name,grade,academic_year,created_at,updated_at) VALUES('导入事务验收','测试','test',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        s1=student("ATOMIC1");s2=student("ATOMIC2");
        e=BusinessRules.id(scores.saveExam(null,new ScoreService.ExamInput("事务验收",c,LocalDate.now())).get("id"));
    }
    long student(String no){return db.insert("INSERT INTO students(student_no,name,class_id,status,created_at,updated_at) VALUES(?,'虚构学生',?,'在读',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",no,c);}
    @AfterEach void cleanup(){
        db.update("DELETE FROM scores WHERE exam_id=?",e);db.update("DELETE FROM exams WHERE id=?",e);db.update("DELETE FROM students WHERE id IN (?,?)",s1,s2);db.update("DELETE FROM classes WHERE id=?",c);
    }
    ScoreService.ScoreInput input(long s,String value){return new ScoreService.ScoreInput(s,e,"数学",new BigDecimal(value),new BigDecimal("100"),"RECORDED","");}
    MockMultipartFile csv(String body){return new MockMultipartFile("file","scores.csv","text/csv",("学号,姓名,成绩,状态,备注\n"+body).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    @Test void conflictAfterPreviewRollsBackEarlierRows(){
        var p=imports.preview(csv("ATOMIC1,虚构学生,80,,\nATOMIC2,虚构学生,90,,\n"),e,"数学",new BigDecimal("100"),"atomic-admin");
        scores.saveScore(null,input(s2,"60"));
        assertThatThrownBy(()->imports.confirm(p.previewId(),false,"atomic-admin")).hasMessageContaining("整批已回滚");
        assertThat(scores.scores(c,e,s1,null)).isEmpty();
        assertThat((BigDecimal)scores.scores(c,e,s2,null).get(0).get("score")).isEqualByComparingTo("60");
    }
    @Test void overwriteRejectsStalePreviewAndOwnerMismatch(){
        long id=BusinessRules.id(scores.saveScore(null,input(s1,"50")).get("id"));
        var p=imports.preview(csv("ATOMIC1,虚构学生,80,,\n"),e,"数学",new BigDecimal("100"),"atomic-admin");
        assertThatThrownBy(()->imports.confirm(p.previewId(),true,"other-user")).hasMessageContaining("预览");
        scores.saveScore(id,input(s1,"70"));
        assertThatThrownBy(()->imports.confirm(p.previewId(),true,"atomic-admin")).hasMessageContaining("被修改");
        assertThat((BigDecimal)scores.scores(c,e,s1,null).get(0).get("score")).isEqualByComparingTo("70");
    }
    @Test void xlsxTemplateRoundTripAndFormulaRejection()throws Exception {
        byte[] bytes;
        try(var wb=new XSSFWorkbook(new ByteArrayInputStream(imports.template("xlsx")));var out=new ByteArrayOutputStream()){
            var row=wb.getSheetAt(0).createRow(1);row.createCell(0).setCellValue("ATOMIC1");row.createCell(1).setCellValue("虚构学生");row.createCell(2).setCellValue(0);wb.write(out);bytes=out.toByteArray();
        }
        var p=imports.preview(new MockMultipartFile("file","scores.xlsx","application/octet-stream",bytes),e,"数学",new BigDecimal("100"),"atomic-admin");
        assertThat(imports.confirm(p.previewId(),false,"atomic-admin")).containsEntry("success",1);
        assertThat((BigDecimal)scores.scores(c,e,s1,null).get(0).get("score")).isEqualByComparingTo("0");
        try(var wb=new XSSFWorkbook(new ByteArrayInputStream(bytes));var out=new ByteArrayOutputStream()){
            wb.getSheetAt(0).getRow(1).getCell(2).setCellFormula("1+1");wb.write(out);byte[] formula=out.toByteArray();
            assertThatThrownBy(()->imports.preview(new MockMultipartFile("file","formula.xlsx","application/octet-stream",formula),e,"数学",new BigDecimal("100"),"atomic-admin")).hasMessageContaining("公式");
        }
    }
}
