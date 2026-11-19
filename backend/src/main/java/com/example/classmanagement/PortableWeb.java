package com.example.classmanagement;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Profile("portable")
@Controller
public class PortableWeb {
    @GetMapping("/login")
    public String loginPage() {
        return "forward:/index.html";
    }
}
