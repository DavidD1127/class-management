package com.example.classmanagement.business;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.example.classmanagement.business.BusinessRules.*;

@Service @Transactional(readOnly=true)
public class AttendanceService {
    private final BusinessRepository db;private final com.example.classmanagement.recycle.RecycleBinService recycle;
    public AttendanceService(BusinessRepository db,com.example.classmanagement.recycle.RecycleBinService recycle){this.db=db;this.recycle=recycle;}
    public record Input(Long studentId,String type,LocalDate occurredDate,LocalTime occurredTime,String remark,boolean confirmDuplicate) {}
    public List<Map<String,Object>> duplicates(Long recordId,Input i) {
        return db.query("SELECT * FROM attendance WHERE student_id=? AND occurred_date=? AND type=? AND (? IS NULL OR id<>?) ORDER BY id",i.studentId(),i.occurredDate(),i.type(),recordId,recordId);
    }
    @Transactional public Map<String,Object> save(Long recordId,Input i) {
        String actor=teachingRecorder();date(i.occurredDate());
        if(!Set.of("LATE","EARLY_LEAVE","ABSENT").contains(Objects.toString(i.type(),"")))throw new IllegalArgumentException("考勤类型无效");
        // 锁定学生，使同一学生的重复检测与写入在并发请求下仍串行进行。
        db.required("学生","SELECT id FROM students WHERE id=? FOR UPDATE",i.studentId());
        if(recordId!=null)db.one("SELECT id FROM attendance WHERE id=?",recordId);
        if(!duplicates(recordId,i).isEmpty()&&!i.confirmDuplicate())throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"同一学生当日已有同类考勤，请核对详情后明确确认重复记录");
        String note=remark(i.remark(),500);
        if(recordId==null)recordId=db.insert("INSERT INTO attendance(student_id,type,occurred_date,occurred_time,remark,recorder) VALUES(?,?,?,?,?,?)",i.studentId(),i.type(),i.occurredDate(),i.occurredTime(),note,actor);
        else db.update("UPDATE attendance SET student_id=?,type=?,occurred_date=?,occurred_time=?,remark=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",i.studentId(),i.type(),i.occurredDate(),i.occurredTime(),note,recordId);
        return db.one("SELECT * FROM attendance WHERE id=?",recordId);
    }
    @Transactional public void delete(Long recordId){recorder();recycle.delete("attendance",recordId);}
    public List<Map<String,Object>> list(Long studentId,Long classId,LocalDate from,LocalDate to,String type) {
        AssignmentService.range(from,to);
        if(type!=null&&!Set.of("LATE","EARLY_LEAVE","ABSENT").contains(type))throw new IllegalArgumentException("考勤类型无效");
        return db.query("SELECT a.*,s.name AS student_name,s.student_no,s.class_id,c.class_name FROM attendance a JOIN students s ON s.id=a.student_id JOIN classes c ON c.id=s.class_id WHERE (? IS NULL OR a.student_id=?) AND (? IS NULL OR s.class_id=?) AND (? IS NULL OR a.occurred_date>=?) AND (? IS NULL OR a.occurred_date<=?) AND (? IS NULL OR a.type=?) ORDER BY a.occurred_date DESC,a.id DESC",studentId,studentId,classId,classId,from,from,to,to,type,type);
    }
    public List<Map<String,Object>> statistics(Long studentId,Long classId,LocalDate from,LocalDate to) {
        var rows=list(studentId,classId,from,to,null);Map<Long,Map<String,Object>> result=new LinkedHashMap<>();
        for(var row:rows){long student=id(row.get("studentId"));var counts=result.computeIfAbsent(student,k->{var m=new LinkedHashMap<String,Object>();m.put("studentId",student);m.put("studentName",row.get("studentName"));m.put("studentNo",row.get("studentNo"));for(String type:List.of("LATE","EARLY_LEAVE","ABSENT"))m.put(type,0L);return m;});String type=row.get("type").toString();counts.put(type,(Long)counts.get(type)+1);}
        return new ArrayList<>(result.values());
    }
}
