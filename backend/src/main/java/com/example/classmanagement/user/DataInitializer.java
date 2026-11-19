package com.example.classmanagement.user;
import org.springframework.context.annotation.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
@Configuration public class DataInitializer {
 @Bean CommandLineRunner init(UserRepository users,PasswordEncoder encoder,
  @Value("${BOOTSTRAP_ADMIN_USERNAME:admin}") String username,@Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String password){
  return args->{if(!password.isBlank()&&users.findByUsername(username).isEmpty()){
   if(password.length()<12)throw new IllegalArgumentException("初始管理员密码至少 12 位");
   users.save(new User(username,encoder.encode(password),"ADMIN"));
  }};
 }
}
