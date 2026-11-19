package com.example.classmanagement.classroom;
import jakarta.persistence.*; import java.time.*;
@Entity @Table(name="classes", uniqueConstraints=@UniqueConstraint(name="uq_class_business",columnNames={"class_name","grade","academic_year"}))
public class Classroom { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id; @Column(name="class_name",nullable=false) public String className; @Column(nullable=false) public String grade; @Column(name="academic_year",nullable=false) public String academicYear; @Column(name="created_at",nullable=false) public LocalDateTime createdAt; @Column(name="updated_at",nullable=false) public LocalDateTime updatedAt; @PrePersist void create(){createdAt=updatedAt=LocalDateTime.now();} @PreUpdate void update(){updatedAt=LocalDateTime.now();} }
