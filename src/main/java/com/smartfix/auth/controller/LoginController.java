package com.smartfix.auth.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Spring Security owns POST /login and POST /logout. */
@Controller
public class LoginController {
    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
