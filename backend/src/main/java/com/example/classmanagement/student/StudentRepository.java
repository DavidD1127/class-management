package com.example.classmanagement.student;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface StudentRepository extends JpaRepository<Student,Long> {
 boolean existsByStudentNo(String no); boolean existsByClassroomId(Long id); long countByClassroomId(Long id);
 @EntityGraph(attributePaths="classroom") Optional<Student> findByStudentNo(String no);
 @EntityGraph(attributePaths="classroom") List<Student> findByName(String name);
 @Override @EntityGraph(attributePaths="classroom") Optional<Student> findById(Long id);
 @EntityGraph(attributePaths="classroom")
 @Query("select s from Student s where (:classId is null or s.classroom.id = :classId) and (:q = '' or lower(s.name) like lower(concat('%',:q,'%')) or lower(s.studentNo) like lower(concat('%',:q,'%')))")
 Page<Student> search(@Param("q") String q,@Param("classId") Long classId,Pageable pageable);
}
