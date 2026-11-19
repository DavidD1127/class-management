package com.example.classmanagement;

import com.example.classmanagement.classroom.*;
import com.example.classmanagement.student.*;
import com.example.classmanagement.user.*;
import com.fasterxml.jackson.databind.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class PhaseTwoTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired StudentService students;
    @Autowired StudentRepository studentRepo;
    @Autowired ClassroomService classes;
    @Autowired ClassroomRepository classRepo;
    @Autowired StudentImportService imports;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transaction;
    long classId;
    String admin, teacher;
    @BeforeEach void setup()throws Exception {
        // 仅允许 TEST_DB_URL 指定的独立测试库；真实环境禁止使用此测试类。
        try(var connection=jdbc.getDataSource().getConnection()) {
            String url=connection.getMetaData().getURL();
            if(!url.startsWith("jdbc:h2:mem:") && !url.matches("jdbc:mysql://[^/]+/phase2_test(?:[?].*)?"))
                throw new IllegalStateException("拒绝清理非 phase2_test 测试库");
        }
        jdbc.execute("DROP TABLE IF EXISTS test_student_references");
        studentRepo.deleteAll(); classRepo.deleteAll(); users.deleteAll();
        users.save(new User("test-admin",encoder.encode("TestPassword123!"),"ADMIN")); // test-fixture
        users.save(new User("test-teacher",encoder.encode("TestPassword123!"),"TEACHER")); // test-fixture
        classId=classes.save(null,new ClassroomService.Input("高一（1）班","高一","2026-2027")).id;
        admin=login("test-admin"); teacher=login("test-teacher");
    }
    String login(String username)throws Exception {
        String body=mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("username",username,"password","TestPassword123!"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn().getResponse().getContentAsString();
        return "Bearer "+json.readTree(body).at("/data/token").asText();
    }
    StudentService.Input input(String no,String name,long c) { return new StudentService.Input(no,name,"男",c,LocalDate.of(2026,9,1),"在读"); }
    MockMultipartFile csv(String rows) { return new MockMultipartFile("file","students.csv","text/csv",("\uFEFF学号,姓名,班级,年级,学年,性别,入学日期,状态\r\n"+rows).getBytes(StandardCharsets.UTF_8)); }
    String line(String no,String name) { return no+","+name+",高一（1）班,高一,2026-2027,男,2026-09-01,在读\r\n"; }
    long count(String no) { return jdbc.queryForObject("SELECT COUNT(*) FROM students WHERE student_no=?",Long.class,no); }

    @Test void adminCrudPersistsAndSearches()throws Exception {
        var response=mvc.perform(post("/api/students").header("Authorization",admin).contentType("application/json")
                .content(json.writeValueAsString(input("S001","同名学生",classId))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id=json.readTree(response).at("/data/id").asLong();
        assertThat(count("S001")).isEqualTo(1);
        students.save(null,input("S002","同名学生",classId));
        long other=classes.save(null,new ClassroomService.Input("高一（2）班","高一","2026-2027")).id;
        students.save(null,input("S003","其他学生",other));
        assertThat(students.hasSameName("同名学生")).isTrue();
        mvc.perform(get("/api/students").param("q","同名").param("classId",String.valueOf(classId)).param("size","1").header("Authorization",teacher))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2)).andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].className").value("高一（1）班"));
        assertThat(students.search("S003",classId,0,10).getTotalElements()).isZero();
        assertThat(students.search("S001",null,0,10).getTotalElements()).isEqualTo(1);
        mvc.perform(put("/api/students/"+id).header("Authorization",admin).contentType("application/json")
                .content(json.writeValueAsString(new StudentService.Input("S001","修改后","",other,null,"离校")))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT name FROM students WHERE id=?",String.class,id)).isEqualTo("修改后");
        mvc.perform(get("/api/students/"+id).header("Authorization",teacher)).andExpect(jsonPath("$.data.status").value("离校"));
        mvc.perform(delete("/api/students/"+id).header("Authorization",admin)).andExpect(status().isOk());
        assertThat(count("S001")).isZero();
    }
    @Test void validationAndUniqueConstraint()throws Exception {
        Student first=students.save(null,input(" s001 ","同名学生",classId));
        Student second=students.save(null,input("S002","同名学生",classId));
        assertThat(first.studentNo).isEqualTo("S001");
        assertThatThrownBy(()->students.save(null,input("s001","另一个",classId))).hasMessageContaining("学号已存在");
        assertThatThrownBy(()->students.save(second.id,input("S001","改名",classId))).hasMessageContaining("学号已存在");
        assertThat(jdbc.queryForObject("SELECT student_no FROM students WHERE id=?",String.class,second.id)).isEqualTo("S002");
        for(var i:List.of(input("","姓名",classId),input("S003"," ",classId),input("S003","姓名",-1)))
            assertThatThrownBy(()->students.save(null,i)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->jdbc.update("INSERT INTO students(student_no,name,class_id,status,created_at,updated_at) VALUES(?,?,?,'在读',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)","S001","重名",classId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        mvc.perform(get("/api/students/99999999").header("Authorization",admin)).andExpect(status().isNotFound());
        mvc.perform(put("/api/students/99999999").header("Authorization",admin).contentType("application/json").content(json.writeValueAsString(input("N","新生",classId)))).andExpect(status().isNotFound());
        assertThat(studentRepo.count()).isEqualTo(2);
    }
    @Test void classRulesAndCounts()throws Exception {
        mvc.perform(post("/api/classes").header("Authorization",admin).contentType("application/json").content("{\"className\":\"新班级\",\"grade\":\"高二\",\"academicYear\":\"2026\"}")).andExpect(status().isOk());
        students.save(null,input("C1","学生",classId));
        mvc.perform(get("/api/classes").header("Authorization",teacher)).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].studentCount").value(1));
        mvc.perform(delete("/api/classes/"+classId).header("Authorization",admin)).andExpect(status().isOk());
        long recycled=jdbc.queryForObject("SELECT id FROM recycle_items WHERE entity_type='classes' AND record_id=? AND restored_at IS NULL",Long.class,classId);
        mvc.perform(post("/api/recycle-bin/"+recycled+"/restore?batch=true").header("Authorization",admin)).andExpect(status().isOk());
        mvc.perform(put("/api/classes/"+classId).header("Authorization",admin).contentType("application/json").content("{\"className\":\"改名班级\",\"grade\":\"高一\",\"academicYear\":\"2026-2027\"}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT class_name FROM classes WHERE id=?",String.class,classId)).isEqualTo("改名班级");
        long empty=classes.save(null,new ClassroomService.Input("空班级","高一","2026")).id;
        mvc.perform(delete("/api/classes/"+empty).header("Authorization",admin)).andExpect(status().isOk());
        assertThat(classRepo.existsById(empty)).isFalse();
        assertThatThrownBy(()->classes.save(null,new ClassroomService.Input("改名班级","高一","2026-2027"))).hasMessageContaining("已存在");
        assertThatThrownBy(()->classes.save(9999999L,new ClassroomService.Input("不存在","高一","2026"))).isInstanceOf(NoSuchElementException.class);
    }
    @Test void authAndRoleEnforcement()throws Exception {
        mvc.perform(get("/api/students")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/classes")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/students/import/preview").file(csv(line("N1","学生")))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/students/import/confirm").contentType("application/json").content("{\"previewId\":\"x\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/students").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/students").header("Authorization",teacher).contentType("application/json").content(json.writeValueAsString(input("NO","无权",classId)))).andExpect(status().isForbidden());
        mvc.perform(post("/api/classes").header("Authorization",teacher).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/students/1").header("Authorization",teacher)).andExpect(status().isForbidden());
        mvc.perform(post("/api/students/import/confirm").header("Authorization",teacher).contentType("application/json").content("{\"previewId\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/students/import/template").header("Authorization",teacher)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"username\":\"test-admin\",\"password\":\"wrong\"}")).andExpect(status().isUnauthorized());
        assertThat(studentRepo.count()).isZero();
    }
    @Test void localDevelopmentCorsAndTeacherPreview()throws Exception {
        mvc.perform(post("/api/auth/login").header("Origin","http://127.0.0.1:5173")
                .contentType("application/json").content("{\"username\":\"test-admin\",\"password\":\"TestPassword123!\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://127.0.0.1:5173"));
        mvc.perform(post("/api/auth/login").header("Origin","https://untrusted.example")
                .contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/students/import/preview").file(csv(line("PREVIEW","教师预览")))
                .header("Authorization",teacher)).andExpect(status().isOk()).andExpect(jsonPath("$.data.valid").value(1));
        assertThat(count("PREVIEW")).isZero();
    }
    @Test void csvPreviewConfirmAndNoOverwrite()throws Exception {
        students.save(null,input("OLD","原姓名",classId));
        var p=imports.preview(csv(line("NEW","\"张,三\"")+line("OLD","覆盖企图")+line("DUP","重复一")+line("DUP","重复二")+"BAD,学生,不存在班级\r\n"),"test-admin");
        assertThat(p.valid()).isEqualTo(1);assertThat(p.errors()).isEqualTo(4);
        assertThat(count("NEW")).isZero();
        mvc.perform(post("/api/students/import/confirm").header("Authorization",admin).contentType("application/json")
                .content(json.writeValueAsString(Map.of("previewId",p.previewId())))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(1)).andExpect(jsonPath("$.data.failed").value(0)).andExpect(jsonPath("$.data.skipped").value(4));
        assertThat(count("NEW")).isEqualTo(1);
        assertThat(students.findByStudentNo("NEW").name).isEqualTo("张,三");
        assertThat(students.findByStudentNo("NEW").enrollmentDate).isEqualTo(LocalDate.of(2026,9,1));
        assertThat(students.findByStudentNo("OLD").name).isEqualTo("原姓名");
        assertThatThrownBy(()->imports.confirm(p.previewId(),"test-admin")).hasMessageContaining("预览不存在");
    }
    @Test void xlsxAndTemplate()throws Exception {
        byte[] template=imports.template("xlsx");
        byte[] file;
        try(var wb=new XSSFWorkbook(new java.io.ByteArrayInputStream(template));var out=new ByteArrayOutputStream()) {
            var row=wb.getSheetAt(0).createRow(1);
            String[] values={"000123","张三","高一（1）班","高一","2026-2027","女","2026-09-01","在读"};
            for(int i=0;i<values.length;i++)row.createCell(i).setCellValue(values[i]);
            wb.write(out);file=out.toByteArray();
        }
        var p=imports.preview(new MockMultipartFile("file","a.xlsx","application/octet-stream",file),"xlsx");
        assertThat(p.valid()).isEqualTo(1);assertThat(studentRepo.count()).isZero();
        imports.confirm(p.previewId(),"xlsx");
        assertThat(count("000123")).isEqualTo(1);
    }
    @Test void invalidFilesAmbiguousClassAndDate()throws Exception {
        assertThatThrownBy(()->imports.preview(new MockMultipartFile("file","x.xlsx","",new byte[]{1,2,3}),"a")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->imports.preview(new MockMultipartFile("file","x.csv","",new byte[5*1024*1024+1]),"a")).hasMessageContaining("5MB");
        assertThatThrownBy(()->imports.preview(csv(""),"a")).hasMessageContaining("没有学生数据");
        assertThatThrownBy(()->imports.preview(new MockMultipartFile("file","x.csv","","错误表头".getBytes(StandardCharsets.UTF_8)),"a")).hasMessageContaining("表头");
        classes.save(null,new ClassroomService.Input("高一（1）班","高一","2027-2028"));
        var p=imports.preview(csv("A,学生,高一（1）班\r\n"+line("B","学生").replace("2026-09-01","2026-99-99")),"a");
        assertThat(p.errors()).isEqualTo(2);
        assertThat(p.rows().get(0).error()).contains("歧义");
        assertThat(p.rows().get(1).error()).contains("日期");
        try(var wb=new XSSFWorkbook();var out=new ByteArrayOutputStream()){
            wb.createSheet().createRow(0).createCell(0).setCellFormula("1+1");wb.write(out);
            assertThatThrownBy(()->imports.preview(new MockMultipartFile("file","formula.xlsx","",out.toByteArray()),"a")).hasMessageContaining("公式");
        }
        assertThat(studentRepo.count()).isZero();
    }
    @Test void importRollbackAndOwnership() {
        var p=imports.preview(csv(line("FIRST","第一")+line("RACE","冲突")),"owner");
        assertThatThrownBy(()->imports.confirm(p.previewId(),"other")).hasMessageContaining("预览不存在");
        students.save(null,input("RACE","已有记录",classId));
        assertThatThrownBy(()->imports.confirm(p.previewId(),"owner")).hasMessageContaining("学号已存在");
        assertThat(count("FIRST")).isZero();assertThat(count("RACE")).isEqualTo(1);
        assertThat(students.findByStudentNo("RACE").name).isEqualTo("已有记录");
    }
    @Test void concurrentConfirmWritesOnce()throws Exception {
        var p=imports.preview(csv(line("ONCE","一次")),"owner");
        var executor=Executors.newFixedThreadPool(2);
        try{
            var jobs=List.<Callable<Boolean>>of(()->{try{imports.confirm(p.previewId(),"owner");return true;}catch(IllegalArgumentException e){return false;}},
                    ()->{try{imports.confirm(p.previewId(),"owner");return true;}catch(IllegalArgumentException e){return false;}});
            var result=executor.invokeAll(jobs);
            assertThat(result.get(0).get() ^ result.get(1).get()).isTrue();assertThat(count("ONCE")).isEqualTo(1);
        }finally{executor.shutdownNow();}
    }
    @Test void foreignKeyBlocksDeleteWithoutDataLoss()throws Exception {
        long id=students.save(null,input("REF","历史学生",classId)).id;
        jdbc.execute("CREATE TABLE test_student_references(id BIGINT PRIMARY KEY, student_id BIGINT NOT NULL, FOREIGN KEY(student_id) REFERENCES students(id))");
        jdbc.update("INSERT INTO test_student_references VALUES(1,?)",id);
        mvc.perform(delete("/api/students/"+id).header("Authorization",admin)).andExpect(status().isConflict());
        assertThat(count("REF")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM test_student_references",Long.class)).isEqualTo(1);
        students.save(id,new StudentService.Input("REF","历史学生","男",classId,null,"离校"));
        assertThat(students.get(id).status).isEqualTo("离校");
        jdbc.execute("DROP TABLE test_student_references");
    }
}
