package com.example.classmanagement.classroom; import org.springframework.data.jpa.repository.*; import java.util.*; public interface ClassroomRepository extends JpaRepository<Classroom,Long>{boolean existsByClassNameAndGradeAndAcademicYear(String n,String g,String y); java.util.Optional<Classroom> findByClassNameAndGradeAndAcademicYear(String n,String g,String y);}

