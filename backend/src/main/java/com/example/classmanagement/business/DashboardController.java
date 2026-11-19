package com.example.classmanagement.business;

import com.example.classmanagement.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController @RequestMapping("/api/dashboard")
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class DashboardController {
    private final BusinessRepository db;
    public DashboardController(BusinessRepository db) { this.db=db; }

    @GetMapping @Transactional(readOnly=true)
    public ApiResponse<?> overview(@RequestParam(required=false) Long classId,
                                   @RequestParam(required=false) LocalDate from,
                                   @RequestParam(required=false) LocalDate to) {
        if(from==null && to==null) { to=LocalDate.now(ZoneId.of("Asia/Shanghai"));from=to.minusDays(29); }
        if(from==null || to==null || from.isAfter(to) || ChronoUnit.DAYS.between(from,to)>365)
            throw new IllegalArgumentException("请选择起止日期，范围不得超过366天");
        if(classId!=null)db.required("班级","SELECT id FROM classes WHERE id=?",classId);
        var result=new LinkedHashMap<String,Object>();
        result.put("from",from);result.put("to",to);
        result.put("students",db.one("SELECT COUNT(*) AS total FROM students WHERE (? IS NULL OR class_id=?)",classId,classId).get("total"));
        result.put("classes",db.one("SELECT COUNT(*) AS total FROM classes WHERE (? IS NULL OR id=?)",classId,classId).get("total"));
        var latest=db.query("SELECT id,name,exam_date FROM exams WHERE (? IS NULL OR class_id=?) AND exam_date<=? ORDER BY exam_date DESC,id DESC LIMIT 1",classId,classId,to);
        result.put("latestExam",latest.isEmpty()?null:latest.get(0));
        result.put("examAverages",latest.isEmpty()?List.of():db.query("SELECT subject,full_score,COUNT(*) AS recorded,AVG(score) AS average_score FROM scores WHERE exam_id=? AND status='RECORDED' GROUP BY subject,full_score ORDER BY subject,full_score",latest.get(0).get("id")));
        result.put("assignmentStates",db.query("SELECT r.status,COUNT(*) AS total FROM assignment_records r JOIN assignments a ON a.id=r.assignment_id WHERE r.included=TRUE AND a.assigned_date>=? AND a.assigned_date<=? AND (? IS NULL OR a.class_id=?) GROUP BY r.status ORDER BY r.status",from,to,classId,classId));
        result.put("attendance",db.query("SELECT a.occurred_date,a.type,COUNT(*) AS total FROM attendance a JOIN students s ON s.id=a.student_id WHERE a.occurred_date>=? AND a.occurred_date<=? AND (? IS NULL OR s.class_id=?) GROUP BY a.occurred_date,a.type ORDER BY a.occurred_date,a.type",from,to,classId,classId));
        result.put("praises",db.one("SELECT COUNT(*) AS total FROM praises p JOIN students s ON s.id=p.student_id WHERE p.praise_date>=? AND p.praise_date<=? AND (? IS NULL OR s.class_id=?)",from,to,classId,classId).get("total"));
        return ApiResponse.ok(result);
    }
}
