package com.example.classmanagement.classroom;

import com.example.classmanagement.recycle.RecycleBinService;
import com.example.classmanagement.student.StudentRepository;
import com.example.classmanagement.student.StudentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class ClassroomService {
    private final ClassroomRepository classrooms;
    private final StudentRepository students;
    private final RecycleBinService recycleBin;

    public ClassroomService(ClassroomRepository classrooms, StudentRepository students,
                            RecycleBinService recycleBin) {
        this.classrooms = classrooms;
        this.students = students;
        this.recycleBin = recycleBin;
    }

    public record Input(String className, String grade, String academicYear) {}

    public List<Map<String, Object>> all() {
        return classrooms.findAll().stream()
                .map(classroom -> Map.<String, Object>of(
                        "id", classroom.id,
                        "className", classroom.className,
                        "grade", classroom.grade,
                        "academicYear", classroom.academicYear,
                        "studentCount", students.countByClassroomId(classroom.id)))
                .toList();
    }

    public Classroom get(Long id) {
        return classrooms.findById(id)
                .orElseThrow(() -> new NoSuchElementException("班级不存在"));
    }

    @Transactional
    public Classroom save(Long id, Input input) {
        Classroom classroom = id == null ? new Classroom() : get(id);
        String className = StudentService.required(input.className(), "班级名称", 100);
        String grade = StudentService.required(input.grade(), "年级", 50);
        String academicYear = StudentService.required(input.academicYear(), "学年", 20);

        boolean duplicate = classrooms.findByClassNameAndGradeAndAcademicYear(className, grade, academicYear)
                .filter(existing -> !Objects.equals(existing.id, id))
                .isPresent();
        if (duplicate) {
            throw new IllegalArgumentException("该学年年级下班级已存在");
        }

        classroom.className = className;
        classroom.grade = grade;
        classroom.academicYear = academicYear;
        return classrooms.saveAndFlush(classroom);
    }

    @Transactional
    public void delete(Long id) {
        recycleBin.delete("classes", id);
    }
}
