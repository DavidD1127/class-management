package com.example.classmanagement.business;

import com.example.classmanagement.api.ApiResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.time.LocalDate;
import static com.example.classmanagement.business.BusinessRules.page;

/** 表扬记录接口。读操作对教师开放，写操作仅管理员。 */
@RestController
@RequestMapping("/api/praises")
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class PraiseController {
    private final PraiseService service;

    public PraiseController(PraiseService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<?> list(@RequestParam(required = false) Long studentId,
                               @RequestParam(required = false) Long classId,
                               @RequestParam(required = false) LocalDate from,
                               @RequestParam(required = false) LocalDate to,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(page(service.list(studentId, classId, from, to), page, size));
    }

    @GetMapping("/statistics")
    public ApiResponse<?> stats(@RequestParam(required = false) Long studentId,
                                @RequestParam(required = false) Long classId,
                                @RequestParam(required = false) LocalDate from,
                                @RequestParam(required = false) LocalDate to) {
        return ApiResponse.ok(service.statistics(studentId, classId, from, to));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> add(@RequestBody PraiseService.Input i) {
        return ApiResponse.ok(service.save(null, i));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> edit(@PathVariable Long id, @RequestBody PraiseService.Input i) {
        return ApiResponse.ok(service.save(id, i));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }
}
