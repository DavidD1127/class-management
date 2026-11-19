package com.example.classmanagement.classroom;
import com.example.classmanagement.api.ApiResponse;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/classes")
public class ClassroomController {
 private final ClassroomService service;public ClassroomController(ClassroomService s){service=s;}
 @GetMapping public ApiResponse<?> all(){return ApiResponse.ok(service.all());}
 @PostMapping public ApiResponse<?> add(@RequestBody ClassroomService.Input i){return ApiResponse.ok(service.save(null,i));}
 @PutMapping("/{id}") public ApiResponse<?> edit(@PathVariable Long id,@RequestBody ClassroomService.Input i){return ApiResponse.ok(service.save(id,i));}
 @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable Long id){service.delete(id);return ApiResponse.ok(null);}
}
