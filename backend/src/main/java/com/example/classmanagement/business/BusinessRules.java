package com.example.classmanagement.business;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;
import java.util.*;
import static com.example.classmanagement.student.StudentService.required;

public final class BusinessRules {
    private BusinessRules() {}
    public static String recorder() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !auth.isAuthenticated() || auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN")))
            throw new AccessDeniedException("仅管理员可写入业务数据");
        return auth.getName();
    }
    public static String teachingRecorder() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null||!auth.isAuthenticated()||auth.getAuthorities().stream().noneMatch(a->Set.of("ROLE_ADMIN","ROLE_TEACHER").contains(a.getAuthority())))
            throw new AccessDeniedException("仅管理员或教师可执行教学记录操作");
        return auth.getName();
    }
    public static String text(String s,String label,int max) { return required(s,label,max); }
    public static String remark(String s,int max) { if(s==null||s.isBlank())return "";return required(s,"备注或描述",max); }
    public static LocalDate date(LocalDate date) { if(date==null)throw new IllegalArgumentException("日期不能为空");return date; }
    public static long id(Object value) { return ((Number)value).longValue(); }
    public static Map<String,Object> page(List<Map<String,Object>> rows,int page,int size) {
        if(page<0||size<1||size>100)throw new IllegalArgumentException("页码或每页数量无效（1–100 条）");
        int start=(int)Math.min((long)page*size,rows.size());
        return Map.of("content",rows.subList(start,Math.min(start+size,rows.size())),"total",rows.size(),"page",page,"size",size);
    }
}
