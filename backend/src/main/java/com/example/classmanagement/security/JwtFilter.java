package com.example.classmanagement.security;
import com.example.classmanagement.user.UserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component public class JwtFilter extends OncePerRequestFilter {
 private final JwtService jwt; private final UserRepository users;
 public JwtFilter(JwtService j,UserRepository u){jwt=j;users=u;}
 @Override protected boolean shouldNotFilterAsyncDispatch(){return false;}
 protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain c)throws ServletException,java.io.IOException{
  String h=r.getHeader("Authorization");
  if(h!=null&&h.startsWith("Bearer "))try{
   users.findByUsername(jwt.user(h.substring(7))).ifPresent(u->SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u.username,null,java.util.List.of(new SimpleGrantedAuthority("ROLE_"+u.role)))));
  }catch(io.jsonwebtoken.JwtException|IllegalArgumentException e){SecurityContextHolder.clearContext();}
  c.doFilter(r,s);
 }
}
