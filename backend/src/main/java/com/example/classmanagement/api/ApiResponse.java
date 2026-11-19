package com.example.classmanagement.api;
public record ApiResponse<T>(int code,String message,T data){public static <T> ApiResponse<T> ok(T d){return new ApiResponse<>(0,"success",d);} public static <T> ApiResponse<T> error(String m){return new ApiResponse<>(1,m,null);}}
