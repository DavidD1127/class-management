package com.example.classmanagement.business;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.example.classmanagement.business.BusinessRules.*;

@Service @Transactional(readOnly=true)
public class PraiseService {
    private final BusinessRepository db;private final com.example.classmanagement.recycle.RecycleBinService recycle;
    public PraiseService(BusinessRepository db,com.example.classmanagement.recycle.RecycleBinService recycle){this.db=db;this.recycle=recycle;}
    public record Input(Long studentId,LocalDate praiseDate,String reason,String remark) {}
    @Transactional public Map<String,Object> save(Long recordId,Input i) {
        String actor=teachingRecorder();date(i.praiseDate());db.required("学生","SELECT id FROM students WHERE id=? FOR UPDATE",i.studentId());
        String reason=text(i.reason(),"表扬原因",500),note=remark(i.remark(),500);
        if(reason.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\t'))throw new IllegalArgumentException("表扬原因包含非法控制字符");
        if(recordId==null)recordId=db.insert("INSERT INTO praises(student_id,praise_date,reason,remark,recorder) VALUES(?,?,?,?,?)",i.studentId(),i.praiseDate(),reason,note,actor);
        else {db.one("SELECT id FROM praises WHERE id=?",recordId);db.update("UPDATE praises SET student_id=?,praise_date=?,reason=?,remark=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",i.studentId(),i.praiseDate(),reason,note,recordId);}
        return db.one("SELECT * FROM praises WHERE id=?",recordId);
    }
    @Transactional public void delete(Long recordId){recorder();recycle.delete("praises",recordId);}
    public List<Map<String,Object>> list(Long studentId,Long classId,LocalDate from,LocalDate to) {
        AssignmentService.range(from,to);
        return db.query("SELECT p.*,s.name AS student_name,s.student_no,s.class_id,c.class_name FROM praises p JOIN students s ON s.id=p.student_id JOIN classes c ON c.id=s.class_id WHERE (? IS NULL OR p.student_id=?) AND (? IS NULL OR s.class_id=?) AND (? IS NULL OR p.praise_date>=?) AND (? IS NULL OR p.praise_date<=?) ORDER BY p.praise_date DESC,p.id DESC",studentId,studentId,classId,classId,from,from,to,to);
    }
    public List<Map<String,Object>> statistics(Long studentId,Long classId,LocalDate from,LocalDate to) {
        Map<Long,Map<String,Object>> result=new LinkedHashMap<>();
        for(var row:list(studentId,classId,from,to)){
            long student=id(row.get("studentId"));var count=result.computeIfAbsent(student,k->{var m=new LinkedHashMap<String,Object>();m.put("studentId",student);m.put("studentName",row.get("studentName"));m.put("studentNo",row.get("studentNo"));m.put("count",0L);return m;});count.put("count",(Long)count.get("count")+1);
        }
        return new ArrayList<>(result.values());
    }
}
