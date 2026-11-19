package com.example.classmanagement.ai;

import com.example.classmanagement.api.ApiResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import java.security.Principal;

@RestController @RequestMapping("/api/ai") @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class AiAttachmentController {
    private final AiRosterService roster;
    public AiAttachmentController(AiRosterService roster){this.roster=roster;}
    @PostMapping("/attachments") public ApiResponse<?> upload(@RequestParam MultipartFile file,@RequestParam(required=false)Long classId,Principal user){return ApiResponse.ok(roster.upload(file,classId,user.getName()));}
}
