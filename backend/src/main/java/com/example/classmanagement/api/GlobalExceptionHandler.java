package com.example.classmanagement.api;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.*;
@RestControllerAdvice public class GlobalExceptionHandler {
 @ExceptionHandler(NoSuchElementException.class) @ResponseStatus(HttpStatus.NOT_FOUND) public ApiResponse<?> missing(Exception e){return ApiResponse.error(e.getMessage());}
 @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class}) @ResponseStatus(HttpStatus.BAD_REQUEST) public ApiResponse<?> bad(Exception e){return ApiResponse.error(e.getMessage());}
 @ExceptionHandler(DataIntegrityViolationException.class) @ResponseStatus(HttpStatus.CONFLICT) public ApiResponse<?> conflict(){return ApiResponse.error("数据冲突或存在关联记录，操作已回滚；请刷新后重试，已有业务记录的学生请改为离校");}
 @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.web.bind.MissingServletRequestParameterException.class,org.springframework.web.multipart.support.MissingServletRequestPartException.class}) @ResponseStatus(HttpStatus.BAD_REQUEST) public ApiResponse<?> invalid(){return ApiResponse.error("请求字段缺失或格式不正确，请检查日期、编号和必填字段");}
 @ExceptionHandler(MaxUploadSizeExceededException.class) @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE) public ApiResponse<?> large(){return ApiResponse.error("文件不能超过 5MB");}
 @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class) @ResponseStatus(HttpStatus.FORBIDDEN) public ApiResponse<?> denied(){return ApiResponse.error("没有执行此操作的权限");}
 @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class) public ResponseEntity<?> status(org.springframework.web.server.ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(ApiResponse.error(e.getReason()));}
 @ExceptionHandler(Exception.class) @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR) public ApiResponse<?> other(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).error("请求处理失败：{}",e.getClass().getSimpleName());return ApiResponse.error("请求处理失败，请稍后重试");}
}
