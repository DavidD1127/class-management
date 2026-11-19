package com.example.classmanagement.security;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
import java.util.List;
@Configuration @org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 @Bean SecurityFilterChain chain(HttpSecurity h,JwtFilter f)throws Exception{
  return h.csrf(c->c.disable()).cors(c->c.configurationSource(r->{var x=new CorsConfiguration();x.setAllowedOrigins(List.of("http://localhost:5173","http://127.0.0.1:5173"));x.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));x.setAllowedHeaders(List.of("*"));return x;}))
   .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .exceptionHandling(e->e.authenticationEntryPoint((r,s,x)->error(s,401,"请先登录")) .accessDeniedHandler((r,s,x)->error(s,403,"没有执行此操作的权限")))
   .authorizeHttpRequests(a->a.requestMatchers("/api/auth/login","/error").permitAll()
    .requestMatchers(HttpMethod.GET,"/","/login","/index.html","/assets/**","/theme-art/**","/theme-covers/**").permitAll()
    .requestMatchers(HttpMethod.GET,"/api/students/**","/api/classes/**").hasAnyRole("ADMIN","TEACHER")
    .requestMatchers(HttpMethod.POST,"/api/students/import/preview").hasAnyRole("ADMIN","TEACHER")
    .requestMatchers("/api/students/**","/api/classes/**").hasRole("ADMIN")
    .anyRequest().authenticated())
   .addFilterBefore(f,org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class).httpBasic(b->b.disable()).build();
 }
 private static void error(jakarta.servlet.http.HttpServletResponse s,int status,String message)throws java.io.IOException{
  s.setStatus(status);s.setContentType("application/json;charset=UTF-8");s.getWriter().write("{\"code\":1,\"message\":\""+message+"\",\"data\":null}");
 }
}
