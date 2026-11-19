package com.example.classmanagement.business;

import com.example.classmanagement.student.StudentImportService;
import com.example.classmanagement.student.StudentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static com.example.classmanagement.business.BusinessRules.*;

@Service
public class ScoreImportService {
    private final StudentImportService parser;
    private final ScoreService scores;
    private final BusinessRepository db;
    private final TransactionTemplate transaction;
    private final Map<String,Preview> previews=new HashMap<>();
    private static final List<String> HEADERS=List.of("学号","姓名","成绩","状态","备注");
    public record Row(int rowNumber,String studentNo,String name,String score,String error,Long existingId,Map<String,Object> snapshot,ScoreService.ScoreInput input) {}
    public record Preview(String previewId,Instant expiresAt,List<Row> rows,int valid,int errors,int existing) {}
    public ScoreImportService(StudentImportService parser,ScoreService scores,BusinessRepository db,TransactionTemplate tx) {this.parser=parser;this.scores=scores;this.db=db;transaction=tx;}
    public synchronized Preview preview(MultipartFile file,Long examId,String subject,BigDecimal fullScore,String owner) {
        previews.values().removeIf(p->p.expiresAt().isBefore(Instant.now()));
        if(!previews.containsKey(owner)&&previews.size()>=100)throw new IllegalArgumentException("预览繁忙，请稍后重试");
        db.required("考试","SELECT id FROM exams WHERE id=?",examId);subject=text(subject,"科目",50);
        var table=parser.parse(file);
        if(table.isEmpty()||!table.get(0).equals(HEADERS))throw new IllegalArgumentException("请使用模板表头：学号、姓名、成绩、状态、备注");
        Map<String,Integer> counts=new HashMap<>();
        for(var r:table.subList(1,table.size()))if(!r.isEmpty())counts.merge(r.get(0).trim().toUpperCase(Locale.ROOT),1,Integer::sum);
        List<Row> rows=new ArrayList<>();
        for(int n=1;n<table.size();n++) {
            var r=table.get(n);if(r.stream().allMatch(String::isBlank))continue;
            String no=cell(r,0),name=cell(r,1),value=cell(r,2),error=null;Long existingId=null;ScoreService.ScoreInput input=null;
            try {
                if(r.size()>5)throw new IllegalArgumentException("列数超过模板");
                no=StudentService.normalizeNo(no);
                if(counts.getOrDefault(no,0)>1)throw new IllegalArgumentException("文件内学号重复");
                var student=db.required("学生","SELECT id,name FROM students WHERE student_no=?",no);
                if(!Objects.equals(name,student.get("name")))throw new IllegalArgumentException("学号与姓名不匹配");
                String status=cell(r,3);if(status.isBlank()||status.equals("已录入"))status="RECORDED";if(status.equals("缺考"))status="ABSENT";
                input=scores.validate(new ScoreService.ScoreInput(id(student.get("id")),examId,subject,value.isBlank()?null:new BigDecimal(value),fullScore,status,cell(r,4)));
                existingId=scores.existing(input);
            }catch(NumberFormatException e){error="成绩应为数字";}catch(IllegalArgumentException|NoSuchElementException e){error=e.getMessage();}
            rows.add(new Row(n+1,no,name,value,error,existingId,existingId==null?null:db.one("SELECT * FROM scores WHERE id=?",existingId),input));
        }
        if(rows.isEmpty())throw new IllegalArgumentException("文件没有成绩数据");
        int valid=(int)rows.stream().filter(r->r.error()==null).count(),existing=(int)rows.stream().filter(r->r.error()==null&&r.existingId()!=null).count();
        var p=new Preview(UUID.randomUUID().toString(),Instant.now().plusSeconds(900),List.copyOf(rows),valid,rows.size()-valid,existing);previews.put(owner,p);return p;
    }
    public synchronized Map<String,Integer> confirm(String previewId,boolean overwrite,String owner) {
        recorder();var p=previews.get(owner);
        if(p==null||!p.previewId().equals(previewId)||p.expiresAt().isBefore(Instant.now()))throw new IllegalArgumentException("预览不存在或过期，请重新上传");
        if(p.valid()==0)throw new IllegalArgumentException("没有有效成绩");
        previews.remove(owner);
        return transaction.execute(tx->{
            var first=p.rows().stream().filter(r->r.error()==null).findFirst().orElseThrow();
            db.required("考试","SELECT id FROM exams WHERE id=? FOR UPDATE",first.input().examId());
            int success=0,skipped=p.errors();
            for(var r:p.rows())if(r.error()==null) {
                Long current=scores.existing(r.input());
                if(!Objects.equals(current,r.existingId()))throw new IllegalArgumentException("预览后成绩发生变化，整批已回滚，请重新预览");
                if(current!=null&&!overwrite){skipped++;continue;}
                if(current!=null&&!db.one("SELECT * FROM scores WHERE id=?",current).equals(r.snapshot()))throw new IllegalArgumentException("预览后已有成绩被修改，整批已回滚，请重新预览");
                scores.saveScore(current,r.input());success++;
            }
            return Map.of("success",success,"failed",0,"skipped",skipped);
        });
    }
    private static String cell(List<String> row,int n){return n<row.size()?row.get(n).trim():"";}
    public byte[] template(String format)throws IOException {
        if(format.equals("csv"))return ("\uFEFF"+String.join(",",HEADERS)+"\r\n").getBytes(StandardCharsets.UTF_8);
        if(!format.equals("xlsx"))throw new IllegalArgumentException("仅支持 csv 或 xlsx");
        try(var wb=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
            var sheet=wb.createSheet("成绩");var row=sheet.createRow(0);var style=wb.createCellStyle();style.setDataFormat(wb.createDataFormat().getFormat("@"));
            for(int i=0;i<HEADERS.size();i++){row.createCell(i).setCellValue(HEADERS.get(i));sheet.setColumnWidth(i,22*256);sheet.setDefaultColumnStyle(i,style);}wb.write(out);return out.toByteArray();
        }
    }
}
