package com.example.classmanagement.student;
import com.example.classmanagement.classroom.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
@Service @Transactional(readOnly=true)
public class StudentService {
 private final StudentRepository repo; private final ClassroomRepository classes; private final com.example.classmanagement.recycle.RecycleBinService recycle;
 public StudentService(StudentRepository r,ClassroomRepository c,com.example.classmanagement.recycle.RecycleBinService recycle){repo=r;classes=c;this.recycle=recycle;}
 public record Input(String studentNo,String name,String gender,Long classId,LocalDate enrollmentDate,String status){}
 public Page<Student> search(String q,Long classId,int page,int size){
  if(page<0||size<1||size>100)throw new IllegalArgumentException("页码或每页数量无效（1–100 条）");
  return repo.search(q==null?"":q.trim(),classId,PageRequest.of(page,size,Sort.by("id").descending()));
 }
 public Student get(Long id){return repo.findById(id).orElseThrow(()->new NoSuchElementException("学生不存在"));}
 public List<Student> findByName(String name){return repo.findByName(required(name,"姓名",100));}
 public boolean hasSameName(String name){return findByName(name).size()>1;}
 public Student findByStudentNo(String no){return repo.findByStudentNo(normalizeNo(no)).orElseThrow(()->new NoSuchElementException("学生不存在"));}
 public static String required(String value,String label,int max){
  if(value==null||value.isBlank())throw new IllegalArgumentException(label+"不能为空");
  String v=value.trim();if(v.length()>max)throw new IllegalArgumentException(label+"长度不能超过 "+max);return v;
 }
 public static String normalizeNo(String no){
  String v=required(no,"学号",50).toUpperCase(Locale.ROOT);
  if(!v.matches("[A-Z0-9_-]+"))throw new IllegalArgumentException("学号仅允许字母、数字、下划线和短横线");return v;
 }
 public Input validate(Input i,Long id){
  String no=normalizeNo(i.studentNo()),name=required(i.name(),"学生姓名",100);
  if(i.classId()==null||!classes.existsById(i.classId()))throw new IllegalArgumentException("所属班级不存在");
  repo.findByStudentNo(no).filter(s->!Objects.equals(s.id,id)).ifPresent(s->{throw new IllegalArgumentException("学号已存在："+no);});
  String gender=i.gender()==null?"":i.gender().trim();
  if(!Set.of("","男","女","其他").contains(gender))throw new IllegalArgumentException("性别应为男、女、其他或留空");
  String status=i.status()==null||i.status().isBlank()?"在读":i.status().trim();
  if(!Set.of("在读","离校").contains(status))throw new IllegalArgumentException("状态应为在读或离校");
  return new Input(no,name,gender,i.classId(),i.enrollmentDate(),status);
 }
 @Transactional public Student save(Long id,Input input){
  Student s=id==null?new Student():get(id);Input i=validate(input,id);
  s.studentNo=i.studentNo();s.name=i.name();s.gender=i.gender();s.classroom=classes.findById(i.classId()).orElseThrow(()->new IllegalArgumentException("所属班级不存在"));
  s.enrollmentDate=i.enrollmentDate();s.status=i.status();return repo.saveAndFlush(s);
 }
 @Transactional public void delete(Long id){recycle.delete("students",id);}
 public Map<String,Object> view(Student s){return Map.of("id",s.id,"studentNo",s.studentNo,"name",s.name,"gender",Objects.toString(s.gender,""),"classId",s.classroom.id,"className",s.classroom.className,"enrollmentDate",Objects.toString(s.enrollmentDate,""),"status",s.status);}
}
