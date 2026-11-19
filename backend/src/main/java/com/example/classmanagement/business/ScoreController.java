package com.example.classmanagement.business;

import com.example.classmanagement.api.ApiResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import java.math.BigDecimal;
import java.security.Principal;
import static com.example.classmanagement.business.BusinessRules.page;

/** 考试与成绩接口。查询对教师开放；创建、修改与导入仅管理员。 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class ScoreController {
    private final ScoreService service;
    private final ScoreImportService imports;

    public ScoreController(ScoreService service, ScoreImportService imports) {
        this.service = service;
        this.imports = imports;
    }

    @GetMapping("/exams")
    public ApiResponse<?> exams(@RequestParam(required = false) Long classId) {
        return ApiResponse.ok(service.exams(classId));
    }

    @PostMapping("/exams")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> add(@RequestBody ScoreService.ExamInput i) {
        return ApiResponse.ok(service.saveExam(null, i));
    }

    @PutMapping("/exams/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> edit(@PathVariable Long id, @RequestBody ScoreService.ExamInput i) {
        return ApiResponse.ok(service.saveExam(id, i));
    }

    @GetMapping("/scores")
    public ApiResponse<?> scores(@RequestParam(required = false) Long classId,
                                 @RequestParam(required = false) Long examId,
                                 @RequestParam(required = false) Long studentId,
                                 @RequestParam(required = false) String subject,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(page(service.scores(classId, examId, studentId, subject), page, size));
    }

    @GetMapping("/scores/summary")
    public ApiResponse<?> summary(@RequestParam(required = false) Long classId,
                                  @RequestParam(required = false) Long examId,
                                  @RequestParam(required = false) Long studentId,
                                  @RequestParam(required = false) String subject,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(page(service.summary(classId, examId, studentId, subject), page, size));
    }

    @GetMapping("/scores/statistics")
    public ApiResponse<?> stats(@RequestParam(required = false) Long classId,
                                @RequestParam(required = false) Long examId,
                                @RequestParam(required = false) String subject) {
        return ApiResponse.ok(service.statistics(classId, examId, subject));
    }

    @GetMapping("/scores/history/{studentId}")
    public ApiResponse<?> history(@PathVariable Long studentId,
                                 @RequestParam(required = false) String subject) {
        return ApiResponse.ok(service.scores(null, null, studentId, subject));
    }

    @PostMapping("/scores")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> addScore(@RequestBody ScoreService.ScoreInput i) {
        return ApiResponse.ok(service.saveScore(null, i));
    }

    @PutMapping("/scores/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> editScore(@PathVariable Long id, @RequestBody ScoreService.ScoreInput i) {
        return ApiResponse.ok(service.saveScore(id, i));
    }

    @GetMapping("/scores/import/template")
    public ResponseEntity<byte[]> template(@RequestParam(defaultValue = "xlsx") String format) throws Exception {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=scores." + format)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(imports.template(format));
    }

    @PostMapping("/scores/import/preview")
    public ApiResponse<?> preview(@RequestPart MultipartFile file,
                                  @RequestParam Long examId,
                                  @RequestParam String subject,
                                  @RequestParam BigDecimal fullScore,
                                  Principal user) {
        return ApiResponse.ok(imports.preview(file, examId, subject, fullScore, user.getName()));
    }

    public record Confirm(String previewId, boolean overwrite) {}

    @PostMapping("/scores/import/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> confirm(@RequestBody Confirm i, Principal user) {
        return ApiResponse.ok(imports.confirm(i.previewId(), i.overwrite(), user.getName()));
    }
}
