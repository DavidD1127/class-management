package com.example.classmanagement.student;

import com.example.classmanagement.classroom.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import java.util.zip.*;

@Service
public class StudentImportService {
    private static final int MAX_ROWS = 2000;
    private static final List<String> HEADERS = List.of("学号","姓名","班级","年级","学年","性别","入学日期","状态");
    private final StudentService students;
    private final ClassroomRepository classes;
    private final TransactionTemplate transaction;
    // 单进程部署：每账号仅保留一份预览，重启失效；多实例部署时改用共享存储。
    private final Map<String, Preview> previews = new HashMap<>();
    public record Row(int rowNumber, String studentNo, String name, String className,
                      String gender, String enrollmentDate, String status, String error, StudentService.Input input) {}
    public record Preview(String previewId, String owner, Instant expiresAt, List<Row> rows, int valid, int errors) {}
    public StudentImportService(StudentService students, ClassroomRepository classes, TransactionTemplate transaction) {
        this.students=students; this.classes=classes; this.transaction=transaction;
        ZipSecureFile.setMaxEntrySize(10 * 1024 * 1024);
    }
    public synchronized Preview preview(MultipartFile file, String owner) {
        return previewTable(parse(file),owner);
    }
    public synchronized Preview previewTable(List<List<String>> table,String owner) {
        if(table==null||table.size()>MAX_ROWS+1)throw new IllegalArgumentException("最多导入2000行");
        for(var row:table)if(row==null||row.size()>HEADERS.size()||row.stream().anyMatch(v->v==null||v.length()>200))throw new IllegalArgumentException("名单字段过长或格式无效");
        previews.values().removeIf(p -> p.expiresAt().isBefore(Instant.now()));
        if (!previews.containsKey(owner) && previews.size() >= 100) throw new IllegalArgumentException("导入预览繁忙，请稍后重试");
        if (table.isEmpty() || table.get(0).size() < 3 || !table.get(0).subList(0,3).equals(HEADERS.subList(0,3)))
            throw new IllegalArgumentException("表头前三列必须为：学号、姓名、班级；请下载模板");
        List<String> header = table.get(0);
        if (new HashSet<>(header).size()!=header.size() || !HEADERS.containsAll(header))
            throw new IllegalArgumentException("表头包含重复或不支持的字段，请使用模板");
        var allClasses = classes.findAll();
        Map<String,Long> counts = new HashMap<>();
        for (var r : table.subList(1,table.size())) {
            if (!r.isEmpty()) counts.merge(r.get(0).trim().toUpperCase(Locale.ROOT),1L,Long::sum);
        }
        List<Row> out = new ArrayList<>();
        for (int index=1; index<table.size(); index++) {
            var r=table.get(index);
            if (r.stream().allMatch(String::isBlank)) continue;
            String no=value(r,header,"学号"), name=value(r,header,"姓名"), cls=value(r,header,"班级");
            String gender=value(r,header,"性别"), date=value(r,header,"入学日期"), status=value(r,header,"状态");
            String error=null;
            StudentService.Input input=null;
            try {
                if (r.size()>header.size()) throw new IllegalArgumentException("数据列数超过表头");
                no=StudentService.normalizeNo(no);
                if (counts.getOrDefault(no,0L)>1) throw new IllegalArgumentException("文件内学号重复");
                String grade=value(r,header,"年级"), year=value(r,header,"学年");
                var matches=allClasses.stream().filter(c -> c.className.equals(cls)
                        && (grade.isBlank() || c.grade.equals(grade)) && (year.isBlank() || c.academicYear.equals(year))).toList();
                if(matches.isEmpty()) throw new IllegalArgumentException("班级不存在，请先创建班级");
                if(matches.size()>1) throw new IllegalArgumentException("班级名称有歧义，请填写年级和学年");
                input=students.validate(new StudentService.Input(no,name,gender,matches.get(0).id,
                        date.isBlank()?null:LocalDate.parse(date),status),null);
            } catch (java.time.format.DateTimeParseException e) {
                error="入学日期格式应为 yyyy-MM-dd";
            } catch (IllegalArgumentException e) { error=e.getMessage(); }
            out.add(new Row(index+1,no,name,cls,gender,date,status,error,input));
        }
        if(out.isEmpty()) throw new IllegalArgumentException("文件没有学生数据");
        int valid=(int)out.stream().filter(r->r.error()==null).count();
        Preview result=new Preview(UUID.randomUUID().toString(),owner,Instant.now().plusSeconds(900),List.copyOf(out),valid,out.size()-valid);
        previews.put(owner,result);
        return result;
    }
    public synchronized Map<String,Integer> confirm(String previewId,String owner) {
        Preview p=previews.get(owner);
        if(p==null || !p.previewId().equals(previewId) || p.expiresAt().isBefore(Instant.now()))
            throw new IllegalArgumentException("预览不存在或已过期，请重新上传");
        if(p.valid()==0) throw new IllegalArgumentException("没有可导入的有效记录");
        // 删除一次性令牌后执行事务，任何并发冲突都会回滚全部写入并要求重新预览。
        previews.remove(owner);
        transaction.executeWithoutResult(tx -> {
            for(Row r:p.rows()) if(r.error()==null) students.save(null,r.input());
        });
        return Map.of("success",p.valid(),"failed",0,"skipped",p.errors());
    }
    private static String value(List<String> row,List<String> header,String key) {
        int i=header.indexOf(key);return i<0||i>=row.size()?"":row.get(i).trim();
    }
    public List<List<String>> parse(MultipartFile file) {
        if(file.isEmpty()||file.getSize()>5*1024*1024) throw new IllegalArgumentException("文件为空或超过 5MB");
        String filename=Objects.toString(file.getOriginalFilename(),"").toLowerCase(Locale.ROOT);
        List<List<String>> rows=new ArrayList<>();
        try {
            byte[] bytes=file.getBytes();
            if(filename.endsWith(".csv")) {
                String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
                if(text.startsWith("\uFEFF")) text=text.substring(1);
                if(text.indexOf('\0')>=0) throw new IllegalArgumentException("CSV 包含非法内容");
                try(var parser=CSVFormat.DEFAULT.parse(new StringReader(text))) {
                    for(var record:parser) {
                        List<String> row=new ArrayList<>();record.forEach(row::add);
                        addRow(rows,row);
                    }
                }
            } else if(filename.endsWith(".xlsx")) {
                checkArchive(bytes);
                try(var wb=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                    if(wb.getNumberOfSheets()!=1) throw new IllegalArgumentException("导入文件只能包含一个工作表");
                    var sheet=wb.getSheetAt(0);
                    if(sheet.getLastRowNum()>MAX_ROWS) throw new IllegalArgumentException("最多导入 2000 行");
                    DataFormatter formatter=new DataFormatter(Locale.ROOT);
                    for(int n=0;n<=sheet.getLastRowNum();n++) {
                        var row=sheet.getRow(n); List<String> cells=new ArrayList<>();
                        if(row!=null) for(int i=0;i<row.getLastCellNum();i++) {
                            if(i>=HEADERS.size()) throw new IllegalArgumentException("文件列数超过模板限制");
                            var c=row.getCell(i);
                            if(c!=null && c.getCellType()==CellType.FORMULA) throw new IllegalArgumentException("Excel 不允许包含公式，请粘贴为值");
                            String value=c==null?"":formatter.formatCellValue(c).trim();
                            if(c!=null&&c.getCellType()==CellType.NUMERIC&&DateUtil.isCellDateFormatted(c))
                                value=c.getLocalDateTimeCellValue().toLocalDate().toString();
                            cells.add(value);
                        }
                        addRow(rows,cells);
                    }
                }
            } else throw new IllegalArgumentException("仅支持 .xlsx 和 UTF-8 编码的 .csv");
        } catch(IllegalArgumentException e) { throw e; }
        catch(Exception e) { throw new IllegalArgumentException("文件损坏、编码错误或内容不符合格式，请使用模板重新导出"); }
        return rows;
    }
    private static void addRow(List<List<String>> rows,List<String> row) {
        if(rows.size()>MAX_ROWS) throw new IllegalArgumentException("最多导入 2000 行");
        if(row.size()>HEADERS.size() || row.stream().anyMatch(s->s.length()>200))
            throw new IllegalArgumentException("文件列数或单元格长度超过限制");
        rows.add(row);
    }
    private static void checkArchive(byte[] bytes)throws IOException {
        long total=0;int entries=0;boolean contentTypes=false;
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;byte[] buffer=new byte[8192];
            while((entry=zip.getNextEntry())!=null) {
                if(++entries>100) throw new IllegalArgumentException("Excel 压缩包条目过多");
                if(entry.getName().equals("[Content_Types].xml")) contentTypes=true;
                int n;while((n=zip.read(buffer))!=-1) {
                    total+=n;if(total>20*1024*1024) throw new IllegalArgumentException("Excel 解压内容超过 20MB");
                }
            }
        }
        if(!contentTypes) throw new IllegalArgumentException("文件不是有效的 XLSX");
    }
    public byte[] template(String format)throws IOException {
        if("csv".equals(format)) return ("\uFEFF"+String.join(",",HEADERS)+"\r\n").getBytes(StandardCharsets.UTF_8);
        if(!"xlsx".equals(format)) throw new IllegalArgumentException("模板格式应为 csv 或 xlsx");
        try(var wb=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
            var sheet=wb.createSheet("学生名单");var header=sheet.createRow(0);
            var textStyle=wb.createCellStyle();textStyle.setDataFormat(wb.createDataFormat().getFormat("@"));
            for(int i=0;i<HEADERS.size();i++) {header.createCell(i).setCellValue(HEADERS.get(i));sheet.setColumnWidth(i,20*256);sheet.setDefaultColumnStyle(i,textStyle);}
            wb.write(out);return out.toByteArray();
        }
    }
}
