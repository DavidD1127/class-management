package com.example.classmanagement.ai;

import com.example.classmanagement.api.ApiResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController @RequestMapping("/api/ai/operations") @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class AiOperationController {
    private final WriteTools writes;
    public AiOperationController(WriteTools w){writes=w;}
    @GetMapping("/{id}") public ApiResponse<?> view(@PathVariable String id){return ApiResponse.ok(writes.view(id));}
    @PostMapping("/{id}/confirm") public ApiResponse<?> confirm(@PathVariable String id){return ApiResponse.ok(writes.confirm(id));}
    @PostMapping("/{id}/cancel") public ApiResponse<?> cancel(@PathVariable String id){return ApiResponse.ok(writes.cancel(id));}
}
