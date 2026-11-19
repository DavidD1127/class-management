package com.example.classmanagement.student;
import com.example.classmanagement.api.ApiResponse;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/students")
public class StudentController {
 private final StudentService service; public StudentController(StudentService s){service=s;}
 @GetMapping public ApiResponse<?> page(@RequestParam(defaultValue="")String q,@RequestParam(required=false)Long classId,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="10")int size){
  var x=service.search(q,classId,page,size);return ApiResponse.ok(Map.of("content",x.getContent().stream().map(service::view).toList(),"page",x.getNumber(),"size",x.getSize(),"total",x.getTotalElements(),"pages",x.getTotalPages()));
 }
 @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable Long id){return ApiResponse.ok(service.view(service.get(id)));}
 @PostMapping public ApiResponse<?> add(@RequestBody StudentService.Input i){return ApiResponse.ok(service.view(service.save(null,i)));}
 @PutMapping("/{id}") public ApiResponse<?> edit(@PathVariable Long id,@RequestBody StudentService.Input i){return ApiResponse.ok(service.view(service.save(id,i)));}
 @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable Long id){service.delete(id);return ApiResponse.ok(null);}
}
