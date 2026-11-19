package com.example.classmanagement;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class) public class ClassManagementApplication { public static void main(String[] args){SpringApplication.run(ClassManagementApplication.class,args);} }
