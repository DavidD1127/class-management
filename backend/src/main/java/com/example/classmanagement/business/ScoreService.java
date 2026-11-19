package com.example.classmanagement.business;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.util.*;
import static com.example.classmanagement.business.BusinessRules.*;

@Service @Transactional(readOnly=true)
public class ScoreService {
    private final BusinessRepository db;
    public ScoreService(BusinessRepository db){this.db=db;}
    public record ExamInput(String name,Long classId,LocalDate examDate) {}
    public record ScoreInput(Long studentId,Long examId,String subject,BigDecimal score,BigDecimal fullScore,String status,String remark) {}
    public List<Map<String,Object>> exams(Long classId) {
        return db.query("SELECT e.*,c.class_name FROM exams e JOIN classes c ON c.id=e.class_id WHERE (? IS NULL OR e.class_id=?) ORDER BY e.exam_date DESC,e.id DESC",classId,classId);
    }
    @Transactional public Map<String,Object> saveExam(Long examId,ExamInput i) {
        recorder();String name=text(i.name(),"考试名称",100);date(i.examDate());
        db.required("班级","SELECT id FROM classes WHERE id=?",i.classId());
        if(examId==null)examId=db.insert("INSERT INTO exams(name,class_id,exam_date) VALUES(?,?,?)",name,i.classId(),i.examDate());
        else {
            var old=db.required("考试","SELECT * FROM exams WHERE id=? FOR UPDATE",examId);
            if(!Objects.equals(id(old.get("classId")),i.classId()) && !db.query("SELECT id FROM scores WHERE exam_id=?",examId).isEmpty())
                throw new IllegalArgumentException("已有成绩的考试不能更换班级");
            db.update("UPDATE exams SET name=?,class_id=?,exam_date=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",name,i.classId(),i.examDate(),examId);
        }
        return db.required("考试","SELECT * FROM exams WHERE id=?",examId);
    }
    @Transactional(readOnly=true,noRollbackFor={IllegalArgumentException.class,NoSuchElementException.class})
    public ScoreInput validate(ScoreInput i) {
        var exam=db.required("考试","SELECT * FROM exams WHERE id=?",i.examId());
        var student=db.required("学生","SELECT * FROM students WHERE id=?",i.studentId());
        if(id(exam.get("classId"))!=id(student.get("classId")))throw new IllegalArgumentException("学生不属于考试班级");
        String subject=text(i.subject(),"科目",50),status=i.status()==null?"RECORDED":i.status();
        if(i.fullScore()==null||i.fullScore().signum()<=0||i.fullScore().compareTo(new BigDecimal("99999999.99"))>0||i.fullScore().scale()>2)
            throw new IllegalArgumentException("满分必须大于零，最多两位小数且不超过99999999.99");
        if(!Set.of("RECORDED","ABSENT").contains(status))throw new IllegalArgumentException("成绩状态应为 RECORDED 或 ABSENT");
        if(status.equals("ABSENT")) { if(i.score()!=null)throw new IllegalArgumentException("缺考记录不能填写分数"); }
        else if(i.score()==null||i.score().signum()<0||i.score().compareTo(i.fullScore())>0||i.score().scale()>2)
            throw new IllegalArgumentException("成绩不能为空、不得低于零或超过满分，最多两位小数");
        return new ScoreInput(i.studentId(),i.examId(),subject,i.score(),i.fullScore(),status,remark(i.remark(),500));
    }
    public Long existing(ScoreInput i) {
        var rows=db.query("SELECT id FROM scores WHERE student_id=? AND exam_id=? AND subject=?",i.studentId(),i.examId(),i.subject());
        return rows.isEmpty()?null:id(rows.get(0).get("id"));
    }
    @Transactional public Map<String,Object> saveScore(Long scoreId,ScoreInput input) {
        teachingRecorder();
        db.required("考试","SELECT id FROM exams WHERE id=? FOR UPDATE",input.examId());
        db.required("学生","SELECT id FROM students WHERE id=? FOR UPDATE",input.studentId());
        var i=validate(input); Long existing=existing(i);
        if(existing!=null&&!existing.equals(scoreId))throw new IllegalArgumentException("已有成绩，请编辑原记录；不允许重复新增");
        if(scoreId==null)scoreId=db.insert("INSERT INTO scores(student_id,exam_id,subject,score,full_score,status,remark) VALUES(?,?,?,?,?,?,?)",i.studentId(),i.examId(),i.subject(),i.score(),i.fullScore(),i.status(),i.remark());
        else {
            db.one("SELECT id FROM scores WHERE id=?",scoreId);
            db.update("UPDATE scores SET student_id=?,exam_id=?,subject=?,score=?,full_score=?,status=?,remark=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",i.studentId(),i.examId(),i.subject(),i.score(),i.fullScore(),i.status(),i.remark(),scoreId);
        }
        return db.one("SELECT * FROM scores WHERE id=?",scoreId);
    }
    public List<Map<String,Object>> scores(Long classId,Long examId,Long studentId,String subject) {
        return db.query("SELECT s.*,st.name AS student_name,st.student_no,e.name AS exam_name,e.class_id,e.exam_date,c.class_name FROM scores s JOIN students st ON st.id=s.student_id JOIN exams e ON e.id=s.exam_id JOIN classes c ON c.id=e.class_id WHERE (? IS NULL OR e.class_id=?) AND (? IS NULL OR s.exam_id=?) AND (? IS NULL OR s.student_id=?) AND (? IS NULL OR s.subject=?) ORDER BY e.exam_date,s.exam_id,s.subject,s.id",classId,classId,examId,examId,studentId,studentId,subject,subject);
    }
    public List<Map<String,Object>> summary(Long classId,Long examId,Long studentId,String subject) {
        var exams=new LinkedHashMap<Long,List<Map<String,Object>>>();
        for(var score:scores(classId,examId,null,subject)) {
            exams.computeIfAbsent(id(score.get("examId")),key->new ArrayList<>()).add(score);
        }
        List<Map<String,Object>> result=new ArrayList<>();
        for(var exam:exams.values()) {
            var subjects=exam.stream().map(score->(String)score.get("subject")).distinct().toList();
            var students=new TreeMap<String,List<Map<String,Object>>>();
            for(var score:exam) {
                if(studentId==null||studentId==id(score.get("studentId"))) {
                    students.computeIfAbsent((String)score.get("studentNo"),key->new ArrayList<>()).add(score);
                }
            }
            for(var grades:students.values()) {
                var first=grades.get(0);
                var row=new LinkedHashMap<String,Object>();
                for(var key:List.of("examId","examName","studentId","studentNo","studentName"))row.put(key,first.get(key));
                row.put("subjects",subjects);
                row.put("grades",grades);
                // 以同场考试已有成绩的科目为范围，缺考和漏录均不能算作零分。
                if(subject==null&&grades.size()==subjects.size()&&grades.stream().allMatch(g->"RECORDED".equals(g.get("status")))) {
                    row.put("totalScore",grades.stream().map(g->(BigDecimal)g.get("score")).reduce(BigDecimal.ZERO,BigDecimal::add));
                    row.put("totalFullScore",grades.stream().map(g->(BigDecimal)g.get("fullScore")).reduce(BigDecimal.ZERO,BigDecimal::add));
                }
                result.add(row);
            }
        }
        return result;
    }
    public List<Map<String,Object>> statistics(Long classId,Long examId,String subject) {
        Map<String,List<Map<String,Object>>> groups=new LinkedHashMap<>();
        for(var row:scores(classId,examId,null,subject))groups.computeIfAbsent(row.get("examId")+":"+row.get("subject"),k->new ArrayList<>()).add(row);
        List<Map<String,Object>> result=new ArrayList<>();
        for(var group:groups.values()) {
            var first=group.get(0); var valid=new ArrayList<>(group.stream().filter(r->"RECORDED".equals(r.get("status"))).toList());
            valid.sort((a,b)->compareRatio(b,a));
            List<Map<String,Object>> ranks=new ArrayList<>(); int rank=0;Map<String,Object> previous=null;BigDecimal total=BigDecimal.ZERO,rawTotal=BigDecimal.ZERO;
            for(int n=0;n<valid.size();n++) {
                var row=valid.get(n);BigDecimal r=ratio(row);if(previous==null||compareRatio(row,previous)!=0)rank=n+1;previous=row;total=total.add(r);rawTotal=rawTotal.add((BigDecimal)row.get("score"));
                var ranked=new LinkedHashMap<>(row);ranked.put("rank",rank);ranked.put("percentage",r.multiply(BigDecimal.valueOf(100)).setScale(2,RoundingMode.HALF_UP));ranks.add(ranked);
            }
            long passed=valid.stream().filter(r->ratio(r).compareTo(new BigDecimal("0.6"))>=0).count();
            var out=new LinkedHashMap<String,Object>();out.put("examId",first.get("examId"));out.put("examName",first.get("examName"));out.put("subject",first.get("subject"));out.put("recorded",valid.size());out.put("absent",group.size()-valid.size());
            out.put("averagePercentage",valid.isEmpty()?null:total.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(valid.size()),2,RoundingMode.HALF_UP));
            out.put("averageScore",valid.isEmpty()?null:rawTotal.divide(BigDecimal.valueOf(valid.size()),2,RoundingMode.HALF_UP));
            out.put("highestScore",valid.stream().map(r->(BigDecimal)r.get("score")).max(BigDecimal::compareTo).orElse(null));out.put("lowestScore",valid.stream().map(r->(BigDecimal)r.get("score")).min(BigDecimal::compareTo).orElse(null));
            out.put("passed",passed);out.put("passRate",valid.isEmpty()?null:BigDecimal.valueOf(passed*100).divide(BigDecimal.valueOf(valid.size()),2,RoundingMode.HALF_UP));out.put("ranking",ranks);result.add(out);
        }
        return result;
    }
    private static BigDecimal ratio(Map<String,Object> r) { return ((BigDecimal)r.get("score")).divide((BigDecimal)r.get("fullScore"),12,RoundingMode.HALF_UP); }
    public Map<String,Object> leaders(Long classId,Long examId,String subject) {
        List<String> subjects=subject==null?List.of("语文","数学","英语"):List.of(text(subject,"科目",50));
        var scope=db.query("SELECT c.id,c.grade,c.academic_year FROM classes c WHERE (? IS NULL OR c.id=?) AND EXISTS(SELECT 1 FROM students s WHERE s.class_id=c.id)",classId,classId);
        if(scope.isEmpty())throw new IllegalArgumentException("这个班还没有学生，暂时没有成绩可查。");
        if(scope.stream().map(c->c.get("grade")+":"+c.get("academicYear")).distinct().count()>1)throw new IllegalArgumentException("想比较哪个年级的成绩？");
        Set<Long> classIds=new HashSet<>();scope.forEach(c->classIds.add(id(c.get("id"))));
        var exams=exams(classId);var data=scores(classId,null,null,subject).stream().filter(r->subjects.contains(r.get("subject"))).toList();
        List<Map<String,Object>> candidates;
        if(examId!=null)candidates=List.of(db.required("考试","SELECT * FROM exams WHERE id=?",examId));
        else candidates=exams;
        Map<String,Object> chosen=null;List<Map<String,Object>> selected=List.of();
        for(var candidate:candidates){
            var matching=exams.stream().filter(e->e.get("name").equals(candidate.get("name"))&&e.get("examDate").equals(candidate.get("examDate"))).toList();
            Set<Long> examIds=new HashSet<>(),matchedClasses=new HashSet<>();boolean duplicate=false;
            for(var e:matching){examIds.add(id(e.get("id")));if(!matchedClasses.add(id(e.get("classId"))))duplicate=true;}
            if(duplicate)throw new IllegalArgumentException("有同名同日的考试，想查哪一场？");
            var rows=data.stream().filter(r->examIds.contains(id(r.get("examId")))).toList();
            Set<Long> recordedClasses=new HashSet<>();rows.forEach(r->recordedClasses.add(id(r.get("classId"))));
            if(matchedClasses.containsAll(classIds)&&recordedClasses.containsAll(classIds)){chosen=candidate;selected=rows;break;}
        }
        if(chosen==null)throw new IllegalArgumentException("还没找到这些班都录了成绩的同一场考试，想先看哪个班？");
        Map<String,BigDecimal> full=new LinkedHashMap<>();
        for(var row:selected){String key=row.get("subject").toString();BigDecimal value=(BigDecimal)row.get("fullScore"),previous=full.putIfAbsent(key,value);if(previous!=null&&previous.compareTo(value)!=0)throw new IllegalArgumentException("各班这科的满分不一样，想先看哪个班？");}
        if(!full.keySet().containsAll(subjects))throw new IllegalArgumentException("这场考试的语数英还没录齐，想先看哪一科？");
        Map<Long,List<Map<String,Object>>> byStudent=new LinkedHashMap<>();for(var row:selected)byStudent.computeIfAbsent(id(row.get("studentId")),k->new ArrayList<>()).add(row);
        int students=((Number)db.one("SELECT COUNT(*) AS total FROM students WHERE (? IS NULL OR class_id=?)",classId,classId).get("total")).intValue();
        var ranking=new ArrayList<Map<String,Object>>();int absent=0,incomplete=Math.max(0,students-byStudent.size());BigDecimal fullScore=full.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);
        for(var rows:byStudent.values()){
            if(rows.stream().anyMatch(r->!"RECORDED".equals(r.get("status")))){absent++;continue;}
            if(rows.size()!=subjects.size()||rows.stream().map(r->r.get("subject")).distinct().count()!=subjects.size()){incomplete++;continue;}
            var first=rows.get(0);var row=new LinkedHashMap<String,Object>();for(String key:List.of("studentId","studentNo","studentName","classId","className"))row.put(key,first.get(key));
            row.put("score",rows.stream().map(r->(BigDecimal)r.get("score")).reduce(BigDecimal.ZERO,BigDecimal::add));row.put("fullScore",fullScore);ranking.add(row);
        }
        ranking.sort((a,b)->{int compare=((BigDecimal)b.get("score")).compareTo((BigDecimal)a.get("score"));return compare==0?a.get("studentNo").toString().compareTo(b.get("studentNo").toString()):compare;});
        int rank=0;BigDecimal previous=null;for(int n=0;n<ranking.size();n++){var row=ranking.get(n);BigDecimal value=(BigDecimal)row.get("score");if(previous==null||previous.compareTo(value)!=0)rank=n+1;row.put("rank",rank);previous=value;}
        var out=new LinkedHashMap<String,Object>();out.put("examName",chosen.get("name"));out.put("examDate",chosen.get("examDate"));out.put("subject",subject==null?"语数英总分":subject);out.put("fullScore",full.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add));out.put("recorded",ranking.size());out.put("absent",absent);out.put("incomplete",incomplete);out.put("ranking",ranking);return out;
    }
    private static int compareRatio(Map<String,Object> a,Map<String,Object> b) {return ((BigDecimal)a.get("score")).multiply((BigDecimal)b.get("fullScore")).compareTo(((BigDecimal)b.get("score")).multiply((BigDecimal)a.get("fullScore")));}
}
