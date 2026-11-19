package com.example.classmanagement.ai;

import com.example.classmanagement.business.*;
import com.example.classmanagement.student.*;
import com.example.classmanagement.classroom.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.format.*;
import java.util.*;

@Service @Transactional(readOnly=true) @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class ReadOnlyTools {
    private final StudentService students;private final ClassroomService classes;private final ScoreService scores;private final AssignmentService assignments;private final AttendanceService attendance;private final PraiseService praises;
    public ReadOnlyTools(StudentService s,ClassroomService c,ScoreService sc,AssignmentService a,AttendanceService at,PraiseService p){students=s;classes=c;scores=sc;assignments=a;attendance=at;praises=p;}
    private static final Set<String> KINDS=Set.of("students","classes","exams","scores","score_statistics","score_leaders","assignment_catalog","assignments","assignment_statistics","attendance","attendance_statistics","praises","praise_statistics");
    public List<Map<String,Object>> definitions(){
        Map<String,Object> properties=new LinkedHashMap<>();
        properties.put("kind",Map.of("type","string","enum",KINDS));
        for(String key:List.of("studentId","classId","examId","assignmentId"))properties.put(key,Map.of("type","integer","minimum",1));
        for(String key:List.of("name","studentNo","subject","status","from","to","period"))properties.put(key,Map.of("type","string","maxLength",100));
        return List.of(Map.of("type","function","function",Map.of("name","query_school","description","只读业务查询。问谁最高分、谁第一优先用score_leaders；只允许kind/classId/examId/subject。examId省略时自动查最近一次已录成绩的共同考试，subject省略时比较语数英总分，有科目则比该科原始分；不需要得分率或额外权重。同分会返回并列排名，缺考或缺科不加入总分。先查classes确定班级；students精确姓名/学号消歧并获取studentId，再查询业务。不可猜测ID。成绩按examId科目统计；assignment_statistics必须assignmentId。日期可用from/to(YYYY-MM-DD)或period：今天、昨天、最近七天、最近一周、最近一个月、上个月、上周一、全部。无日期默认全部。status为考勤或作业状态。返回后端统计和范围，不能自行估算。classes只允许kind；exams只允许kind/classId，省略classId时返回全部班级的考试记录，可据此回答「最近一次考试是哪场」这类问题；禁止from/to/period；score_statistics只允许kind/classId/examId/subject；assignment_statistics只允许kind/assignmentId。个人作业完成状态用assignments并传已确认的studentId，不用assignment_catalog或全班统计替代。","parameters",Map.of("type","object","properties",properties,"required",List.of("kind"),"additionalProperties",false))));
    }
    public Object execute(String tool,JsonNode a,Set<Long> resolved){
        if(!"query_school".equals(tool)||!a.isObject())throw new IllegalArgumentException("AI 工具不存在或参数格式非法");
        var allowed=Set.of("kind","studentId","classId","examId","assignmentId","name","studentNo","subject","status","from","to","period");
        a.fieldNames().forEachRemaining(k->{if(!allowed.contains(k))throw new IllegalArgumentException("AI 工具包含未知参数");});
        for(String k:allowed)if(a.has(k)){
            JsonNode v=a.get(k);if(k.endsWith("Id")){if(!v.isIntegralNumber()||!v.canConvertToLong()||v.asLong()<1)throw new IllegalArgumentException("AI 工具编号非法");}
            else if(!v.isTextual()||v.asText().isBlank()||v.asText().length()>100)throw new IllegalArgumentException("AI 工具文本参数非法");
        }
        String kind=text(a,"kind");if(!KINDS.contains(Objects.toString(kind,"")))throw new IllegalArgumentException("AI 查询类型不存在");
        Set<String> valid=new HashSet<>(switch(kind){
            case "students"->Set.of("studentId","classId","name","studentNo");case "classes"->Set.of();case "exams"->Set.of("classId");
            case "scores"->Set.of("studentId","classId","examId","subject","period","from","to");case "score_statistics","score_leaders"->Set.of("classId","examId","subject");
            case "assignment_catalog"->Set.of("classId","subject","period","from","to");case "assignments"->Set.of("assignmentId","studentId","classId","subject","period","from","to","status");
            case "assignment_statistics"->Set.of("assignmentId");case "attendance"->Set.of("studentId","classId","period","from","to","status");
            default->Set.of("studentId","classId","period","from","to");});valid.add("kind");
        a.fieldNames().forEachRemaining(k->{if(!valid.contains(k))throw new IllegalArgumentException("该查询类型不支持参数："+k);});
        Long student=id(a,"studentId"),classId=id(a,"classId"),exam=id(a,"examId"),assignment=id(a,"assignmentId");
        if(classId!=null)classes.get(classId);
        String name=text(a,"name"),no=text(a,"studentNo"),subject=text(a,"subject"),status=text(a,"status");
        if(!kind.equals("students")&&(name!=null||no!=null))throw new IllegalArgumentException("请先通过学生工具确认身份");
        if(kind.equals("students")){
            if(student!=null&&!resolved.contains(student))throw new IllegalArgumentException("请先按姓名或学号确认学生，不能猜测ID");
            List<Student> matches=student!=null?List.of(students.get(student)):no!=null?List.of(students.findByStudentNo(no)):name!=null?students.findByName(name):students.search("",classId,0,100).getContent();
            matches=matches.stream().filter(s->classId==null||s.classroom.id.equals(classId)).filter(s->name==null||s.name.equals(name)).filter(s->no==null||s.studentNo.equalsIgnoreCase(no)).toList();
            var views=matches.stream().map(s->Map.<String,Object>of("id",s.id,"studentNo",s.studentNo,"name",s.name,"classId",s.classroom.id,"className",s.classroom.className,"grade",s.classroom.grade,"academicYear",s.classroom.academicYear,"status",s.status)).toList();
            if(matches.size()==1)resolved.add(matches.get(0).id);
            return Map.of("matches",views,"requiresChoice",name!=null&&matches.size()>1,"notice",matches.isEmpty()?"没有匹配学生":matches.size()>1&&name!=null?"存在同名学生，必须请用户选择学号，不得自行选择":"只返回必要档案，名单最多100条；更多请缩小班级范围");
        }
        if(student!=null&&!resolved.contains(student))throw new IllegalArgumentException("学生身份尚未确认，请先按姓名或学号查询");
        LocalDate[] dates=dates(text(a,"period"),text(a,"from"),text(a,"to"),LocalDate.now(ZoneId.of("Asia/Shanghai")));
        LocalDate from=dates[0],to=dates[1];
        Object result=switch(kind){
            case "classes"->classes.all();case "exams"->scores.exams(classId);
            case "scores"->scores.scores(classId,exam,student,subject).stream().filter(r->within(r.get("examDate"),from,to)).toList();
            case "score_statistics"->{if(exam==null)throw new IllegalArgumentException("成绩统计请先选择明确的考试ID");yield scores.statistics(classId,exam,subject);}
            case "score_leaders"->scores.leaders(classId,exam,subject);
            case "assignment_catalog"->assignments.list(classId,subject,from,to);
            case "assignments"->assignments.records(assignment,student,classId,subject,from,to,status);
            case "assignment_statistics"->{if(assignment==null)throw new IllegalArgumentException("作业统计必须指定作业ID");yield assignments.statistics(assignment);}
            case "attendance"->attendance.list(student,classId,from,to,status);
            case "attendance_statistics"->attendance.statistics(student,classId,from,to);
            case "praises"->praises.list(student,classId,from,to);
            case "praise_statistics"->praises.statistics(student,classId,from,to);
            default->throw new IllegalArgumentException("不支持的查询");
        };
        // 超范围时拒绝，避免把截断的明细误认为完整统计。
        if(result instanceof List<?> rows&&rows.size()>100)throw new IllegalArgumentException("查询超过100条，请缩小班级、学生或日期范围");
        String range=kind.equals("score_leaders")?((Map<?,?>)result).get("examDate").toString():from==null?"全部已记录日期":from+" 至 "+to;
        return Map.of("range",range,"data",sanitize(result),"rules","缺考不算零分；无记录不等于表现差；未录入不计入统计。不同满分比较得分率；无考试可比性标记，不能断言进步最大。语数英总分由score_leaders按分数直接相加，不另设权重；不能自定义其他综合评价。作业COMPLETED及LATE为完成，included=false排除，UNCONFIRMED为到期待确认。统计工具范围以指定考试/作业为准。");
    }
    private Object sanitize(Object value){
        if(value instanceof Map<?,?> map){Map<String,Object> out=new LinkedHashMap<>();map.forEach((k,v)->{if(!Set.of("remark","recorder","createdAt","updatedAt","description").contains(k.toString()))out.put(k.toString(),sanitize(v));});return out;}
        if(value instanceof List<?> list)return list.stream().map(this::sanitize).toList();return value;
    }
    private static boolean within(Object value,LocalDate from,LocalDate to){LocalDate d=LocalDate.parse(value.toString());return from==null||(!d.isBefore(from)&&!d.isAfter(to));}
    static String text(JsonNode a,String k){return a.has(k)?a.get(k).asText():null;}static Long id(JsonNode a,String k){return a.has(k)?a.get(k).asLong():null;}
    public static LocalDate[] dates(String period,String from,String to,LocalDate today){
        if(period!=null&&(from!=null||to!=null))throw new IllegalArgumentException("日期范围不能同时使用相对日期和明确日期");
        if(period!=null)return switch(period){
            case "全部"->new LocalDate[]{null,null};case "今天"->new LocalDate[]{today,today};case "昨天"->new LocalDate[]{today.minusDays(1),today.minusDays(1)};
            case "最近七天","最近一周"->new LocalDate[]{today.minusDays(6),today};case "最近一个月"->new LocalDate[]{today.minusMonths(1),today};
            case "上个月"->new LocalDate[]{today.minusMonths(1).withDayOfMonth(1),today.withDayOfMonth(1).minusDays(1)};
            case "上周一"->{var d=today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);yield new LocalDate[]{d,d};}
            default->throw new IllegalArgumentException("日期表达不明确，请提供明确日期范围");};
        if(from==null&&to==null)return new LocalDate[]{null,null};
        try{var f=LocalDate.parse(from,DateTimeFormatter.ISO_LOCAL_DATE);var t=LocalDate.parse(to,DateTimeFormatter.ISO_LOCAL_DATE);AssignmentService.range(f,t);return new LocalDate[]{f,t};}catch(Exception e){throw new IllegalArgumentException("请同时提供有效的开始和结束日期");}
    }
}
