package com.example.classmanagement.recycle;

import com.example.classmanagement.business.BusinessRepository;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import static com.example.classmanagement.business.BusinessRules.teachingRecorder;

@Service @Transactional @PreAuthorize("hasRole('ADMIN')")
public class RecycleBinService {
    private final BusinessRepository db;private final ObjectMapper json;
    // 顺序为外键依赖顺序；删除反序，恢复正序。表名和字段只能由后端提供。
    private static final List<String> TABLES=List.of("classes","students","exams","assignments","scores","assignment_records","attendance","praises");
    public static final Map<String,String> TYPES=Map.of("classes","班级","students","学生","exams","考试","assignments","作业","scores","成绩","assignment_records","作业记录","attendance","考勤","praises","表扬");
    public RecycleBinService(BusinessRepository db,ObjectMapper json){this.db=db;this.json=json;}
    private String type(String type){if(!TYPES.containsKey(Objects.toString(type,"")))throw new IllegalArgumentException("回收站数据类型无效");return type;}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("回收数据编码失败");}}
    private Map<String,Object> decode(Object value){try{return json.readerFor(Map.class).with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(value.toString());}catch(Exception e){throw new IllegalArgumentException("回收数据读取失败");}}
    private String fingerprint(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(value).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private List<Map<String,Object>> rows(String table,String where,Object...args){return db.query("SELECT * FROM "+table+" WHERE "+where+" ORDER BY id FOR UPDATE",args);}
    private Map<String,List<Map<String,Object>>> plan(String type,long id){
        type(type);if(id<1)throw new IllegalArgumentException("记录编号无效");
        Map<String,List<Map<String,Object>>> out=new LinkedHashMap<>();TABLES.forEach(t->out.put(t,new ArrayList<>()));
        var root=rows(type,"id=?",id);if(root.isEmpty())throw new NoSuchElementException(TYPES.get(type)+"不存在或已在回收站");out.put(type,root);
        if(type.equals("classes")){
            out.put("students",rows("students","class_id=?",id));out.put("exams",rows("exams","class_id=?",id));out.put("assignments",rows("assignments","class_id=?",id));
            out.put("scores",rows("scores","student_id IN (SELECT id FROM students WHERE class_id=?) OR exam_id IN (SELECT id FROM exams WHERE class_id=?)",id,id));
            out.put("assignment_records",rows("assignment_records","student_id IN (SELECT id FROM students WHERE class_id=?) OR assignment_id IN (SELECT id FROM assignments WHERE class_id=?)",id,id));
            out.put("attendance",rows("attendance","student_id IN (SELECT id FROM students WHERE class_id=?)",id));out.put("praises",rows("praises","student_id IN (SELECT id FROM students WHERE class_id=?)",id));
        }else if(type.equals("students")){
            for(String table:List.of("scores","assignment_records","attendance","praises"))out.put(table,rows(table,"student_id=?",id));
        }else if(type.equals("exams"))out.put("scores",rows("scores","exam_id=?",id));
        else if(type.equals("assignments"))out.put("assignment_records",rows("assignment_records","assignment_id=?",id));
        if(out.values().stream().mapToInt(List::size).sum()>10000)throw new IllegalArgumentException("本次关联记录超过10000条，请分批操作");
        return out;
    }
    private String label(String type,Map<String,Object> row){
        String label=TYPES.get(type)+" #"+row.get("id");
        if(type.equals("classes"))label=row.get("className")+" · "+row.get("academicYear");
        else if(type.equals("students"))label=row.get("name")+"（"+row.get("studentNo")+"）";
        else if(row.get("name")!=null)label=row.get("name").toString();
        else if(row.get("studentId")!=null){
            var found=db.query("SELECT name,student_no FROM students WHERE id=?",row.get("studentId"));
            if(!found.isEmpty())label=found.get(0).get("name")+"（"+found.get(0).get("studentNo")+"） · "+TYPES.get(type)+" #"+row.get("id");
        }
        return label.substring(0,Math.min(255,label.length()));
    }
    public Map<String,Object> previewDelete(String type,long id){
        var data=plan(type,id);Map<String,Object> counts=new LinkedHashMap<>();data.forEach((t,r)->{if(!r.isEmpty())counts.put(t,r.size());});
        return Map.of("kind","recycle_delete","label",label(type,data.get(type).get(0)),"counts",counts,"total",data.values().stream().mapToInt(List::size).sum(),"fingerprint",fingerprint(data),"rule","确认后移入回收站，包含关联记录；可恢复，不永久删除。");
    }
    public Map<String,Object> delete(String type,long id){
        String actor=teachingRecorder();var data=plan(type,id);String batch=UUID.randomUUID().toString();String title=label(type,data.get(type).get(0));
        db.update("INSERT INTO recycle_batches(id,root_type,root_id,label,deleted_by) VALUES(?,?,?,?,?)",batch,type,id,title,actor);
        int count=0;
        for(String table:TABLES)for(var row:data.get(table)){db.insert("INSERT INTO recycle_items(batch_id,entity_type,record_id,label,payload) VALUES(?,?,?,?,?)",batch,table,row.get("id"),label(table,row),encode(row));count++;}
        var reverse=new ArrayList<>(TABLES);Collections.reverse(reverse);
        for(String table:reverse)for(var row:data.get(table))db.update("DELETE FROM "+table+" WHERE id=?",row.get("id"));
        return Map.of("batchId",batch,"deleted",count,"label",title);
    }
    @Transactional(readOnly=true) public Map<String,Object> list(String type,String search,int page,int size){
        if(type!=null&&!type.isBlank())type(type);else type=null;
        if(page<0||size<1||size>100)throw new IllegalArgumentException("页码或每页数量无效");
        String q=search==null?"":search.trim();if(q.length()>100)throw new IllegalArgumentException("搜索内容过长");
        String where=" FROM recycle_items i JOIN recycle_batches b ON b.id=i.batch_id WHERE i.restored_at IS NULL AND (? IS NULL OR i.entity_type=?) AND (?='' OR LOCATE(?,i.label)>0 OR LOCATE(?,b.label)>0)";
        Object[] args={type,type,q,q,q};long total=((Number)db.one("SELECT COUNT(*) AS total"+where,args).get("total")).longValue();
        var parameters=new ArrayList<>(Arrays.asList(args));parameters.add(size);parameters.add((long)page*size);
        var content=db.query("SELECT i.id,i.batch_id,i.entity_type AS type,i.record_id,i.label,b.label AS source,b.deleted_at,b.deleted_by,(SELECT COUNT(*) FROM recycle_items x WHERE x.batch_id=i.batch_id AND x.restored_at IS NULL) AS batch_count"+where+" ORDER BY i.id DESC LIMIT ? OFFSET ?",parameters.toArray());
        return Map.of("content",content,"total",total,"types",db.query("SELECT entity_type AS type,COUNT(*) AS total FROM recycle_items WHERE restored_at IS NULL GROUP BY entity_type"));
    }
    private List<Map<String,Object>> restoreRows(long recycleId,boolean batch){
        var item=db.required("回收记录","SELECT * FROM recycle_items WHERE id=? FOR UPDATE",recycleId);
        if(item.get("restoredAt")!=null)return List.of();
        return batch?db.query("SELECT * FROM recycle_items WHERE batch_id=? AND restored_at IS NULL ORDER BY id FOR UPDATE",item.get("batchId")):List.of(item);
    }
    public Map<String,Object> previewRestore(long recycleId,boolean batch){
        var rows=restoreRows(recycleId,batch);if(rows.isEmpty())throw new IllegalArgumentException("记录已经恢复，请刷新回收站");
        Map<String,Object> counts=new LinkedHashMap<>();for(String t:TABLES){long n=rows.stream().filter(r->t.equals(r.get("entityType"))).count();if(n>0)counts.put(t,n);}
        var root=db.required("回收来源","SELECT label FROM recycle_batches WHERE id=?",rows.get(0).get("batchId"));
        return Map.of("kind","recycle_restore","label",batch?root.get("label"):rows.get(0).get("label"),"counts",counts,"total",rows.size(),"fingerprint",fingerprint(rows),"rule","按依赖顺序恢复原始编号及关联。冲突时整批回滚，不覆盖现有数据；缺少所属班级、学生、考试或作业时请先恢复它们或恢复整组。");
    }
    public Map<String,Object> restore(long recycleId,boolean batch){
        String actor=teachingRecorder();var rows=restoreRows(recycleId,batch);int restored=0;
        try{
            for(String table:TABLES)for(var item:rows)if(table.equals(item.get("entityType"))){
                var payload=decode(item.get("payload"));if(!db.query("SELECT id FROM "+table+" WHERE id=?",item.get("recordId")).isEmpty())throw new IllegalArgumentException("原编号已被占用，未覆盖数据，请先处理冲突");
                // 快照来自本服务；仍校验字段，避免回收表被篡改后成为SQL入口。
                Set<String> columns=Set.of("id","className","grade","academicYear","createdAt","updatedAt","studentNo","name","gender","classId","enrollmentDate","status","examDate","studentId","examId","subject","score","fullScore","remark","description","assignedDate","deadline","assignmentId","submittedAt","included","type","occurredDate","occurredTime","recorder","praiseDate","reason");
                if(!payload.keySet().stream().allMatch(columns::contains)||!Objects.equals(((Number)payload.get("id")).longValue(),((Number)item.get("recordId")).longValue()))throw new IllegalArgumentException("回收快照字段异常");
                var keys=new ArrayList<>(payload.keySet());String fields=String.join(",",keys.stream().map(k->k.replaceAll("([A-Z])","_$1").toLowerCase(Locale.ROOT)).toList());
                db.update("INSERT INTO "+table+" ("+fields+") VALUES ("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",keys.stream().map(payload::get).toArray());
                db.update("UPDATE recycle_items SET restored_at=CURRENT_TIMESTAMP,restored_by=? WHERE id=?",actor,item.get("id"));restored++;
            }
        }catch(org.springframework.dao.DataIntegrityViolationException e){throw new IllegalArgumentException("恢复失败：学号、班级或业务记录存在冲突，或所属数据尚未恢复。整批未提交，请先处理冲突或恢复整组。",e);}
        return Map.of("restored",restored,"skipped",rows.isEmpty()?1:0);
    }
}
