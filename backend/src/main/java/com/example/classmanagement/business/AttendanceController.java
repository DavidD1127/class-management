package com.example.classmanagement.business;

import com.example.classmanagement.api.ApiResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.time.LocalDate;
import static com.example.classmanagement.business.BusinessRules.page;

@RestController @RequestMapping("/api/attendance") @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class AttendanceController {
    private final AttendanceService service;
    public AttendanceController(AttendanceService service){this.service=service;}
    @GetMapping public ApiResponse<?> list(@RequestParam(required=false)Long studentId,@RequestParam(required=false)Long classId,@RequestParam(required=false)LocalDate from,@RequestParam(required=false)LocalDate to,@RequestParam(required=false)String type,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="10")int size){return ApiResponse.ok(page(service.list(studentId,classId,from,to,type),page,size));}
    @GetMapping("/statistics") public ApiResponse<?> stats(@RequestParam(required=false)Long studentId,@RequestParam(required=false)Long classId,@RequestParam(required=false)LocalDate from,@RequestParam(required=false)LocalDate to){return ApiResponse.ok(service.statistics(studentId,classId,from,to));}
    @PostMapping @PreAuthorize("hasRole('ADMIN')") public ApiResponse<?> add(@RequestBody AttendanceService.Input i){return ApiResponse.ok(service.save(null,i));}
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<?> edit(@PathVariable Long id,@RequestBody AttendanceService.Input i){return ApiResponse.ok(service.save(id,i));}
    @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<?> delete(@PathVariable Long id){service.delete(id);return ApiResponse.ok(null);}
    @PostMapping("/duplicates") public ApiResponse<?> duplicates(@RequestBody AttendanceService.Input i,@RequestParam(required=false)Long id){return ApiResponse.ok(service.duplicates(id,i));}
}
