package com.example.classmanagement.ai;

import com.example.classmanagement.business.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static com.example.classmanagement.business.BusinessRules.*;

/** 模型只生成预览；持久化后只有独立的人工确认接口可以执行业务变更。 */
@Service @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class WriteTools {
    private final BusinessRepository db; private final ObjectMapper json; private final TransactionTemplate tx;
    private final AttendanceService attendance; private final AssignmentService assignments; private final PraiseService praises; private final ScoreService scores;
    private final com.example.classmanagement.recycle.RecycleBinService recycle;
    private static final Set<String> KINDS=Set.of("attendance_add","attendance_edit","assignment_state","praise_add","praise_edit","scores_add","recycle_delete","recycle_restore");
    public WriteTools(BusinessRepository d,ObjectMapper j,TransactionTemplate t,AttendanceService a,AssignmentService as,PraiseService p,ScoreService s,com.example.classmanagement.recycle.RecycleBinService recycle){db=d;json=j;tx=t;attendance=a;assignments=as;praises=p;scores=s;this.recycle=recycle;}
    public List<Map<String,Object>> definitions(){
        Map<String,Object> properties=new LinkedHashMap<>();
        properties.put("kind",Map.of("type","string","enum",KINDS));
        for(String k:List.of("name","studentNo","date","type","time","remark","reason","subject","status","submittedAt"))properties.put(k,Map.of("type","string"));
        for(String k:List.of("classId","examId","assignmentId","recordId","lateMinutes"))properties.put(k,Map.of("type","integer","minimum",1));
        properties.put("fullScore",Map.of("type","number"));
        properties.put("entityType",Map.of("type","string","enum",com.example.classmanagement.recycle.RecycleBinService.TYPES.keySet()));
        properties.put("recycleId",Map.of("type","integer","minimum",1));properties.put("restoreBatch",Map.of("type","boolean"));
        properties.put("rows",Map.of("type","array","maxItems",100,"items",Map.of("type","object","properties",Map.of("name",Map.of("type","string"),"studentNo",Map.of("type","string"),"score",Map.of("type","number")),"additionalProperties",false)));
        return List.of(Map.of("type","function","function",Map.of("name","prepare_school_write","description","仅生成写入预览，不保存业务数据。学生必须提供用户说出的姓名或学号，不能猜。date必须源自用户明确日期或今天/昨天；缺失询问。作业需先查询目录取得明确assignmentId，多个作业询问选择；编辑必须查询明确recordId，多个匹配询问选择。考勤type=LATE/EARLY_LEAVE/ABSENT；作业status=COMPLETED/INCOMPLETE，完成必须询问实际submittedAt(上海本地ISO时间)，不能编造。成绩先查考试，需examId、subject、fullScore和rows，考试或满分未提供询问；绝不创建考试或覆盖成绩。scores_add顶层只允许kind、examId、subject、fullScore、rows，禁止classId/date/name/studentNo；rows每行只允许name、studentNo、score，学生身份只放行内。成绩批次直接将全部行交给本工具校验，不逐行调用students查询；无效身份由预览标为错误行，不能因此中断有效行预览。","parameters",Map.of("type","object","properties",properties,"required",List.of("kind"),"additionalProperties",false))));
    }
    private String encode(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalArgumentException("操作数据编码失败");}}
    private JsonNode decode(Object o){try{return json.readTree(o.toString());}catch(Exception e){throw new IllegalArgumentException("操作数据读取失败");}}
    private static String str(JsonNode a,String key){return a.has(key)?a.get(key).asText():null;}
    private static Long num(JsonNode a,String key){return a.has(key)?a.get(key).longValue():null;}
    private void fields(JsonNode a,Set<String> allowed){
        if(a==null||!a.isObject())throw new IllegalArgumentException("写入参数必须为对象");
        a.fieldNames().forEachRemaining(k->{
            if(!allowed.contains(k)||a.get(k).isNull())throw new IllegalArgumentException("写入参数不支持："+k);
            var v=a.get(k);
            if(k.equals("restoreBatch")){if(!v.isBoolean())throw new IllegalArgumentException("整组恢复参数无效");}
            else if(k.endsWith("Id")||k.equals("lateMinutes")){if(!v.isIntegralNumber()||!v.canConvertToLong()||v.asLong()<1||v.asLong()>Integer.MAX_VALUE)throw new IllegalArgumentException("编号或时长无效");}
            else if(Set.of("score","fullScore").contains(k)){if(!v.isNumber())throw new IllegalArgumentException("分数必须是数字");}
            else if(!k.equals("rows")&&(!v.isTextual()||v.asText().isBlank()||v.asText().length()>500))throw new IllegalArgumentException("文本参数无效："+k);
        });
    }
    private LocalDate date(JsonNode a){
        String d=str(a,"date");if(d==null)throw new IllegalArgumentException("日期缺失，请明确日期");
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        if(d.equals("今天"))return today;if(d.equals("昨天"))return today.minusDays(1);
        try{return LocalDate.parse(d);}catch(Exception e){throw new IllegalArgumentException("请提供明确日期 YYYY-MM-DD");}
    }
    private LocalTime time(String value){try{return LocalTime.parse(value);}catch(Exception e){throw new IllegalArgumentException("发生时间应为 HH:mm 或 HH:mm:ss");}}
    private LocalDateTime submitted(String value){try{return LocalDateTime.parse(value);}catch(Exception e){throw new IllegalArgumentException("提交时间应为上海本地时间 YYYY-MM-DDTHH:mm:ss");}}
    private Map<String,Object> student(JsonNode a,Long classId){
        String no=str(a,"studentNo"),name=str(a,"name");if(no==null&&name==null)throw new IllegalArgumentException("请提供学生姓名或学号");
        var found=db.query("SELECT s.id,s.name,s.student_no,s.class_id,c.class_name FROM students s JOIN classes c ON c.id=s.class_id WHERE (? IS NULL OR s.student_no=?) AND (? IS NULL OR s.name=?) AND (? IS NULL OR s.class_id=?)",no,no,name,name,classId,classId);
        if(found.size()!=1)throw new IllegalArgumentException(found.isEmpty()?"学生不存在或不属于指定班级；不会自动创建学生":"存在同名学生，请选择学号："+found);
        return found.get(0);
    }
    private String table(String kind){return switch(kind){case "attendance_edit"->"attendance";case "praise_edit"->"praises";default->throw new IllegalArgumentException("不支持的记录修改");};}
    private Map<String,Object> original(String kind,Long recordId,long student){
        if(recordId==null)throw new IllegalArgumentException("必须先查询并选择明确的原始记录");
        // 表名来自封闭的后端枚举，不能来自模型。
        return db.required("原始记录","SELECT * FROM "+table(kind)+" WHERE id=? AND student_id=? FOR UPDATE",recordId,student);
    }
    private Map<String,Object> build(JsonNode a){
        String kind=str(a,"kind");if(!KINDS.contains(Objects.toString(kind,"")))throw new IllegalArgumentException("未注册的写操作");
        if(kind.equals("recycle_delete")){fields(a,Set.of("kind","entityType","recordId"));if(!a.has("recordId"))throw new IllegalArgumentException("请先查询并选择明确的目标记录");return recycle.previewDelete(str(a,"entityType"),num(a,"recordId"));}
        if(kind.equals("recycle_restore")){fields(a,Set.of("kind","recycleId","restoreBatch"));if(!a.has("recycleId"))throw new IllegalArgumentException("请先查询回收站并选择记录");return recycle.previewRestore(num(a,"recycleId"),a.path("restoreBatch").asBoolean());}
        Set<String> allowed=new HashSet<>(Set.of("kind"));
        allowed.addAll(switch(kind){
            case "scores_add"->Set.of("examId","subject","fullScore","rows");
            case "attendance_add"->Set.of("name","studentNo","classId","date","type","time","lateMinutes","remark","reason");
            case "attendance_edit"->Set.of("name","studentNo","classId","recordId","remark");
            case "assignment_state"->Set.of("name","studentNo","classId","assignmentId","status","submittedAt","remark");
            case "praise_add"->Set.of("name","studentNo","classId","date","reason","remark");
            default->Set.of("name","studentNo","classId","recordId","reason","remark");
        });fields(a,allowed);
        Map<String,Object> p=new LinkedHashMap<>();p.put("kind",kind);
        if(kind.equals("scores_add")){
            var exam=db.required("考试","SELECT * FROM exams WHERE id=?",num(a,"examId"));
            String subject=text(str(a,"subject"),"科目",50);if(!a.has("fullScore"))throw new IllegalArgumentException("请提供满分");
            JsonNode rows=a.path("rows");if(!rows.isArray()||rows.isEmpty()||rows.size()>100)throw new IllegalArgumentException("每批成绩必须为1至100行");
            List<Map<String,Object>> out=new ArrayList<>();Set<Long> seen=new HashSet<>();Set<Long> duplicated=new HashSet<>();
            for(JsonNode r:rows){
                Map<String,Object> row=new LinkedHashMap<>();row.put("name",str(r,"name"));row.put("studentNo",str(r,"studentNo"));row.put("subject",subject);row.put("examName",exam.get("name"));row.put("examId",exam.get("id"));row.put("score",r.get("score"));row.put("fullScore",a.get("fullScore"));
                try{
                    fields(r,Set.of("name","studentNo","score"));var s=student(r,id(exam.get("classId")));long sid=id(s.get("id"));row.putAll(s);row.put("studentId",sid);
                    if(!seen.add(sid))duplicated.add(sid);
                    if(!r.has("score"))throw new IllegalArgumentException("分数缺失");
                    var input=scores.validate(new ScoreService.ScoreInput(sid,id(exam.get("id")),subject,r.get("score").decimalValue(),a.get("fullScore").decimalValue(),"RECORDED",""));
                    if(scores.existing(input)!=null)throw new IllegalArgumentException("已有成绩，跳过且不覆盖");
                    row.put("state","待确认");
                }catch(IllegalArgumentException|NoSuchElementException e){row.put("state","跳过");row.put("error",e.getMessage());}
                out.add(row);
            }
            for(var row:out)if(duplicated.contains(row.get("studentId"))){row.put("state","跳过");row.put("error","批次内学生重复，全部重复行跳过");}
            p.put("rows",out);p.put("rule","错误和冲突行跳过；有效行在同一事务内提交，任何写入失败整批回滚；已有成绩不覆盖");return p;
        }
        var s=student(a,num(a,"classId"));long sid=id(s.get("id"));p.put("student",s);
        switch(kind){
            case "attendance_add"->{
                LocalDate d=date(a);String type=text(str(a,"type"),"考勤类型",20);if(!Set.of("LATE","EARLY_LEAVE","ABSENT").contains(type))throw new IllegalArgumentException("考勤类型无效");
                if(a.has("lateMinutes")&&!type.equals("LATE"))throw new IllegalArgumentException("只有迟到记录可以填写迟到分钟数");
                p.put("date",d);p.put("type",type);if(a.has("time"))p.put("time",time(str(a,"time")));
                String note=remark(a.has("reason")?str(a,"reason"):str(a,"remark"),500);if(a.has("lateMinutes"))note=(note+" 迟到"+a.get("lateMinutes").asLong()+"分钟").trim();p.put("remark",remark(note,500));
                p.put("duplicates",attendance.duplicates(null,new AttendanceService.Input(sid,type,d,null,note,false)));
            }
            case "attendance_edit","praise_edit"->{
                var old=original(kind,num(a,"recordId"),sid);p.put("before",old);p.put("recordId",num(a,"recordId"));
                if(kind.equals("attendance_edit"))p.put("remark",remark(text(str(a,"remark"),"新备注",500),500));
                else {p.put("reason",text(str(a,"reason"),"表扬原因",500));p.put("remark",a.has("remark")?remark(str(a,"remark"),500):old.get("remark"));}
            }
            case "assignment_state"->{
                Long aid=num(a,"assignmentId");var assignment=db.required("作业","SELECT * FROM assignments WHERE id=?",aid);
                if(id(assignment.get("classId"))!=id(s.get("classId")))throw new IllegalArgumentException("学生不属于作业班级");
                var before=db.required("作业完成记录","SELECT * FROM assignment_records WHERE assignment_id=? AND student_id=? FOR UPDATE",aid,sid);
                String status=text(str(a,"status"),"完成状态",20);if(!Set.of("COMPLETED","INCOMPLETE").contains(status))throw new IllegalArgumentException("状态必须明确为已完成或未完成");
                if(status.equals("COMPLETED")&&!a.has("submittedAt"))throw new IllegalArgumentException("请提供实际提交时间，不能由模型猜测");
                if(status.equals("INCOMPLETE")&&a.has("submittedAt"))throw new IllegalArgumentException("未完成不能填写提交时间");
                if(a.has("submittedAt")){
                    var submitted=submitted(str(a,"submittedAt"));
                    if(submitted.isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai")))||submitted.toLocalDate().isBefore(LocalDate.parse(assignment.get("assignedDate").toString())))throw new IllegalArgumentException("提交时间不能晚于当前时间或早于布置日期");
                    p.put("submittedAt",submitted);
                }
                p.put("assignment",assignment);p.put("before",before);p.put("status",status);p.put("remark",a.has("remark")?remark(str(a,"remark"),500):before.get("remark"));
            }
            case "praise_add"->{p.put("date",date(a));p.put("reason",text(str(a,"reason"),"表扬原因",500));p.put("remark",remark(str(a,"remark"),500));p.put("duplicates",praises.list(sid,null,date(a),date(a)));}
            default->throw new IllegalArgumentException("未注册的写操作");
        }
        return p;
    }
    public Map<String,Object> prepare(JsonNode a){
        String owner=teachingRecorder();long user=id(db.required("当前用户","SELECT id FROM users WHERE username=?",owner).get("id"));
        var preview=build(a);String operation=UUID.randomUUID().toString();
        db.update("INSERT INTO ai_operations(id,user_id,owner,kind,status,payload,preview,expires_at) VALUES(?,?,?,?,?,?,?,?)",operation,user,owner,str(a,"kind"),"PENDING",encode(a),encode(preview),java.sql.Timestamp.from(Instant.now().plusSeconds(900)));
        return view(operation);
    }
    public Map<String,Object> view(String operation){
        String owner=teachingRecorder();var row=db.required("本人操作","SELECT * FROM ai_operations WHERE id=? AND owner=?",operation,owner);
        Map<String,Object> out=new LinkedHashMap<>();for(String k:List.of("id","kind","status","createdAt","expiresAt","failure"))out.put(k,row.get(k));out.put("preview",decode(row.get("preview")));if(row.get("result")!=null)out.put("result",decode(row.get("result")));return out;
    }
    public Map<String,Object> cancel(String operation){
        String owner=teachingRecorder();db.update("UPDATE ai_operations SET status='CANCELLED',updated_at=CURRENT_TIMESTAMP WHERE id=? AND owner=? AND status='PENDING'",operation,owner);return view(operation);
    }
    public Map<String,Object> confirm(String operation){
        String owner=teachingRecorder();
        try{tx.execute(t->{
            var op=db.required("本人操作","SELECT * FROM ai_operations WHERE id=? AND owner=? FOR UPDATE",operation,owner);
            if(!op.get("status").equals("PENDING"))return null;
            var live=db.query("SELECT id FROM ai_operations WHERE id=? AND expires_at>CURRENT_TIMESTAMP",operation);if(live.isEmpty())throw new IllegalArgumentException("预览已过期，请重新生成");
            JsonNode a=decode(op.get("payload")),p=decode(op.get("preview"));
            // 对学生及业务目标加锁后重建预览；任何预览后变更均拒绝，防止覆盖。
            if(str(a,"kind").equals("scores_add"))db.required("考试","SELECT id FROM exams WHERE id=? FOR UPDATE",num(a,"examId"));
            else if(!str(a,"kind").startsWith("recycle_"))db.required("学生","SELECT id FROM students WHERE id=? FOR UPDATE",p.path("student").path("id").longValue());
            if(str(a,"kind").equals("assignment_state"))db.required("作业","SELECT id FROM assignments WHERE id=? FOR UPDATE",num(a,"assignmentId"));
            if(!decode(encode(build(a))).equals(p))throw new IllegalArgumentException("预览后记录发生变化，未写入；请重新预览");
            Object result=execute(a,p);
            db.update("UPDATE ai_operations SET status='SUCCEEDED',result=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",encode(result),operation);return null;
        });}catch(RuntimeException e){
            String message=(e instanceof IllegalArgumentException||e instanceof NoSuchElementException)?e.getMessage():"业务写入失败，事务已回滚，请核对后重新预览";
            tx.execute(t->{db.update("UPDATE ai_operations SET status='FAILED',failure=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND owner=? AND status='PENDING'",message==null?"执行失败":message.substring(0,Math.min(500,message.length())),operation,owner);return null;});
        }
        return view(operation);
    }
    private Object execute(JsonNode a,JsonNode p){
        if(str(a,"kind").equals("recycle_delete"))return recycle.delete(str(a,"entityType"),num(a,"recordId"));
        if(str(a,"kind").equals("recycle_restore"))return recycle.restore(num(a,"recycleId"),a.path("restoreBatch").asBoolean());
        String kind=str(a,"kind");long sid=p.path("student").path("id").asLong();
        if(kind.equals("scores_add")){
            int added=0,skipped=0;List<Object> records=new ArrayList<>();
            for(JsonNode r:p.path("rows")){
                if(!r.path("state").asText().equals("待确认")){skipped++;continue;}
                records.add(scores.saveScore(null,new ScoreService.ScoreInput(r.path("studentId").longValue(),num(a,"examId"),str(a,"subject"),r.path("score").decimalValue(),a.path("fullScore").decimalValue(),"RECORDED","")));added++;
            }
            return Map.of("added",added,"skipped",skipped,"failed",0,"records",records);
        }
        JsonNode old=p.path("before");Map<String,Object> record=switch(kind){
            case "attendance_add"->attendance.save(null,new AttendanceService.Input(sid,str(a,"type"),date(a),a.has("time")?LocalTime.parse(str(a,"time")):null,p.path("remark").asText(),true));
            case "attendance_edit"->attendance.save(num(a,"recordId"),new AttendanceService.Input(sid,old.path("type").asText(),LocalDate.parse(old.path("occurredDate").asText()),old.hasNonNull("occurredTime")?LocalTime.parse(old.path("occurredTime").asText()):null,p.path("remark").asText(),true));
            case "praise_add"->praises.save(null,new PraiseService.Input(sid,date(a),str(a,"reason"),str(a,"remark")));
            case "praise_edit"->praises.save(num(a,"recordId"),new PraiseService.Input(sid,LocalDate.parse(old.path("praiseDate").asText()),str(a,"reason"),p.path("remark").asText()));
            case "assignment_state"->assignments.state(num(a,"assignmentId"),sid,new AssignmentService.StateInput(str(a,"status"),a.has("submittedAt")?LocalDateTime.parse(str(a,"submittedAt")):null,p.path("remark").asText(),old.path("included").asBoolean()));
            default->throw new IllegalArgumentException("未注册的写操作");
        };
        return Map.of("added",kind.endsWith("add")?1:0,"updated",kind.endsWith("add")?0:1,"skipped",0,"failed",0,"record",record);
    }
}
