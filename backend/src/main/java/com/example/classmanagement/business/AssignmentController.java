package com.example.classmanagement.business;

import com.example.classmanagement.api.ApiResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.time.LocalDate;
import static com.example.classmanagement.business.BusinessRules.page;

@RestController @RequestMapping("/api/assignments") @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class AssignmentController {
    private final AssignmentService service;
    public AssignmentController(AssignmentService service){this.service=service;}
    @GetMapping public ApiResponse<?> list(@RequestParam(required=false)Long classId,@RequestParam(required=false)String subject,@RequestParam(required=false)LocalDate from,@RequestParam(required=false)LocalDate to,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="10")int size){return ApiResponse.ok(page(service.list(classId,subject,from,to),page,size));}
    @PostMapping @PreAuthorize("hasRole('ADMIN')") public ApiResponse<?> add(@RequestBody AssignmentService.Input i){return ApiResponse.ok(service.save(null,i));}
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<?> edit(@PathVariable Long id,@RequestBody AssignmentService.Input i){return ApiResponse.ok(service.save(id,i));}
    @GetMapping("/records") public ApiResponse<?> records(@RequestParam(required=false)Long assignmentId,@RequestParam(required=false)Long studentId,@RequestParam(required=false)Long classId,@RequestParam(required=false)String subject,@RequestParam(required=false)LocalDate from,@RequestParam(required=false)LocalDate to,@RequestParam(required=false)String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="10")int size){return ApiResponse.ok(page(service.records(assignmentId,studentId,classId,subject,from,to,status),page,size));}
    @PutMapping("/{id}/students/{studentId}") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<?> state(@PathVariable Long id,@PathVariable Long studentId,@RequestBody AssignmentService.StateInput i){return ApiResponse.ok(service.state(id,studentId,i));}
    @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<?> delete(@PathVariable Long id){return ApiResponse.ok(service.delete(id));}
    @GetMapping("/{id}/statistics") public ApiResponse<?> stats(@PathVariable Long id){return ApiResponse.ok(service.statistics(id));}
}
