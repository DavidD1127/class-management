package com.example.classmanagement.business;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.math.*;
import static com.example.classmanagement.business.BusinessRules.*;

@Service @Transactional(readOnly=true)
public class AssignmentService {
    private final BusinessRepository db;
    private final com.example.classmanagement.recycle.RecycleBinService recycle;
    public AssignmentService(BusinessRepository db,com.example.classmanagement.recycle.RecycleBinService recycle){this.db=db;this.recycle=recycle;}
    public record Input(String name,Long classId,String subject,String description,LocalDate assignedDate,LocalDateTime deadline) {}
    public record StateInput(String status,LocalDateTime submittedAt,String remark,Boolean included) {}
    @Transactional public Map<String,Object> save(Long assignmentId,Input i) {
        recorder();String name=text(i.name(),"作业名称",100),subject=text(i.subject(),"科目",50),description=remark(i.description(),2000);date(i.assignedDate());
        if(i.deadline()==null||i.deadline().toLocalDate().isBefore(i.assignedDate()))throw new IllegalArgumentException("截止时间不能早于布置日期");
        db.required("班级","SELECT id FROM classes WHERE id=?",i.classId());
        if(assignmentId==null) {
            assignmentId=db.insert("INSERT INTO assignments(name,class_id,subject,description,assigned_date,deadline) VALUES(?,?,?,?,?,?)",name,i.classId(),subject,description,i.assignedDate(),i.deadline());
            // 固定布置时的在读名单，后续转班、离校不改写历史统计；管理员可显式排除个别记录。
            db.update("INSERT INTO assignment_records(assignment_id,student_id,status) SELECT ?,id,'PENDING' FROM students WHERE class_id=? AND status='在读'",assignmentId,i.classId());
        } else {
            var old=db.required("作业","SELECT * FROM assignments WHERE id=? FOR UPDATE",assignmentId);
            if(id(old.get("classId"))!=i.classId())throw new IllegalArgumentException("已布置的作业不能更换班级，请新建作业");
            db.update("UPDATE assignments SET name=?,subject=?,description=?,assigned_date=?,deadline=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",name,subject,description,i.assignedDate(),i.deadline(),assignmentId);
            db.update("UPDATE assignment_records SET status=CASE WHEN submitted_at>? THEN 'LATE' ELSE 'COMPLETED' END,updated_at=CURRENT_TIMESTAMP WHERE assignment_id=? AND submitted_at IS NOT NULL",i.deadline(),assignmentId);
        }
        return db.required("作业","SELECT * FROM assignments WHERE id=?",assignmentId);
    }
    public List<Map<String,Object>> list(Long classId,String subject,LocalDate from,LocalDate to) {
        range(from,to);
        return db.query("SELECT a.*,c.class_name FROM assignments a JOIN classes c ON c.id=a.class_id WHERE (? IS NULL OR a.class_id=?) AND (? IS NULL OR a.subject=?) AND (? IS NULL OR a.assigned_date>=?) AND (? IS NULL OR a.assigned_date<=?) ORDER BY a.assigned_date DESC,a.id DESC",classId,classId,subject,subject,from,from,to,to);
    }
    public List<Map<String,Object>> records(Long assignmentId,Long studentId,Long classId,String subject,LocalDate from,LocalDate to,String status) {
        range(from,to);
        if(status!=null&&!Set.of("PENDING","COMPLETED","INCOMPLETE","LATE","UNCONFIRMED").contains(status))throw new IllegalArgumentException("作业状态无效");
        var rows=db.query("SELECT r.*,s.student_no,s.name AS student_name,a.name AS assignment_name,a.class_id,c.class_name,a.subject,a.assigned_date,a.deadline FROM assignment_records r JOIN students s ON s.id=r.student_id JOIN assignments a ON a.id=r.assignment_id JOIN classes c ON c.id=a.class_id WHERE (? IS NULL OR r.assignment_id=?) AND (? IS NULL OR r.student_id=?) AND (? IS NULL OR a.class_id=?) AND (? IS NULL OR a.subject=?) AND (? IS NULL OR a.assigned_date>=?) AND (? IS NULL OR a.assigned_date<=?) ORDER BY a.assigned_date DESC,r.id",assignmentId,assignmentId,studentId,studentId,classId,classId,subject,subject,from,from,to,to);
        for(var r:rows)if("PENDING".equals(r.get("status"))&&LocalDateTime.parse(r.get("deadline").toString()).isBefore(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))))r.put("status","UNCONFIRMED");
        return rows.stream().filter(r->status==null||status.equals(r.get("status"))).toList();
    }
    @Transactional public Map<String,Object> state(Long assignmentId,Long studentId,StateInput i) {
        teachingRecorder();var a=db.required("作业","SELECT * FROM assignments WHERE id=? FOR UPDATE",assignmentId);
        var student=db.required("学生","SELECT * FROM students WHERE id=?",studentId);
        var existing=db.query("SELECT * FROM assignment_records WHERE assignment_id=? AND student_id=?",assignmentId,studentId);
        if(existing.isEmpty()&&id(a.get("classId"))!=id(student.get("classId")))throw new IllegalArgumentException("学生不属于作业班级");
        if(!Set.of("PENDING","COMPLETED","INCOMPLETE","LATE").contains(Objects.toString(i.status(),"")))throw new IllegalArgumentException("作业状态无效");
        LocalDateTime deadline=LocalDateTime.parse(a.get("deadline").toString());String state=i.status();
        if(Set.of("COMPLETED","LATE").contains(state)) {
            if(i.submittedAt()==null)throw new IllegalArgumentException("已完成或迟交必须填写提交时间");
            if(i.submittedAt().isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))))throw new IllegalArgumentException("提交时间不能晚于当前时间");
            if(i.submittedAt().toLocalDate().isBefore(LocalDate.parse(a.get("assignedDate").toString())))throw new IllegalArgumentException("提交时间不能早于布置日期");
            state=i.submittedAt().isAfter(deadline)?"LATE":"COMPLETED";
        } else {
            if(i.submittedAt()!=null)throw new IllegalArgumentException("未提交的状态不能填写提交时间");
            if(state.equals("PENDING")&&deadline.isBefore(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))))throw new IllegalArgumentException("已到期，请确认未完成；未确认记录会单独显示");
        }
        String note=remark(i.remark(),500);boolean included=i.included()==null?true:i.included();
        if(existing.isEmpty())db.update("INSERT INTO assignment_records(assignment_id,student_id,status,submitted_at,remark,included) VALUES(?,?,?,?,?,?)",assignmentId,studentId,state,i.submittedAt(),note,included);
        else db.update("UPDATE assignment_records SET status=?,submitted_at=?,remark=?,included=?,updated_at=CURRENT_TIMESTAMP WHERE assignment_id=? AND student_id=?",state,i.submittedAt(),note,included,assignmentId,studentId);
        return db.one("SELECT * FROM assignment_records WHERE assignment_id=? AND student_id=?",assignmentId,studentId);
    }
    public Map<String,Object> statistics(Long assignmentId) {
        db.required("作业","SELECT id FROM assignments WHERE id=?",assignmentId);
        var all=records(assignmentId,null,null,null,null,null,null);
        var rows=all.stream().filter(r->Boolean.TRUE.equals(r.get("included"))||Objects.equals(r.get("included"),1)).toList();
        Map<String,Object> result=new LinkedHashMap<>();
        for(String state:List.of("PENDING","COMPLETED","INCOMPLETE","LATE","UNCONFIRMED"))result.put(state,rows.stream().filter(r->state.equals(r.get("status"))).count());
        long completed=rows.stream().filter(r->Set.of("COMPLETED","LATE").contains(r.get("status"))).count();
        result.put("denominator",rows.size());result.put("excluded",all.size()-rows.size());result.put("completionRate",rows.isEmpty()?null:BigDecimal.valueOf(completed*100).divide(BigDecimal.valueOf(rows.size()),2,RoundingMode.HALF_UP));return result;
    }
    public static void range(LocalDate from,LocalDate to) {if(from!=null&&to!=null&&from.isAfter(to))throw new IllegalArgumentException("开始日期不能晚于结束日期");}
    /** 删除作业：连同该作业的全部完成记录一起移入回收站，可整批恢复。 */
    @Transactional public Map<String,Object> delete(Long assignmentId){
        db.required("作业","SELECT id FROM assignments WHERE id=?",assignmentId);
        return recycle.delete("assignments",assignmentId);
    }
}
