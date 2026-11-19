package com.example.classmanagement.recycle;

import com.example.classmanagement.api.ApiResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController @RequestMapping("/api/recycle-bin") @PreAuthorize("hasRole('ADMIN')")
public class RecycleBinController {
    private final RecycleBinService service;
    public RecycleBinController(RecycleBinService service){this.service=service;}
    @GetMapping public ApiResponse<?> list(@RequestParam(required=false)String type,@RequestParam(required=false)String q,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return ApiResponse.ok(service.list(type,q,page,size));}
    @GetMapping("/{id}/preview") public ApiResponse<?> preview(@PathVariable long id,@RequestParam(defaultValue="false")boolean batch){return ApiResponse.ok(service.previewRestore(id,batch));}
    @PostMapping("/{id}/restore") public ApiResponse<?> restore(@PathVariable long id,@RequestParam(defaultValue="false")boolean batch){return ApiResponse.ok(service.restore(id,batch));}
}
