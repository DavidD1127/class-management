package com.example.classmanagement.student;
import com.example.classmanagement.api.ApiResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import java.security.Principal;
@RestController @RequestMapping("/api/students/import")
public class StudentImportController {
 private final StudentImportService service;
 public StudentImportController(StudentImportService s){service=s;}
 public record Confirm(String previewId){}
 @PostMapping("/preview") public ApiResponse<?> preview(@RequestParam MultipartFile file,Principal user){return ApiResponse.ok(service.preview(file,user.getName()));}
 @PostMapping("/confirm") public ApiResponse<?> confirm(@RequestBody Confirm input,Principal user){return ApiResponse.ok(service.confirm(input.previewId(),user.getName()));}
 @GetMapping("/template") public ResponseEntity<byte[]> template(@RequestParam(defaultValue="xlsx")String format)throws java.io.IOException{
  byte[] bytes=service.template(format);
  return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=student-template."+format)
   .contentType(MediaType.parseMediaType(format.equals("csv")?"text/csv;charset=UTF-8":"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(bytes);
 }
}
