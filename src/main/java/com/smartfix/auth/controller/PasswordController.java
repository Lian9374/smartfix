package com.smartfix.auth.controller;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.common.exception.InputValidationException;
import com.smartfix.user.service.UserService;
import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class PasswordController {
    private final UserService users;
    public PasswordController(UserService users) { this.users = users; }
    @GetMapping("/account/password")
    public String form(@AuthenticationPrincipal SmartFixUserDetails principal, Model model) {
        model.addAttribute("requiredChange", users.getUserAccess(principal.getUserId()).passwordChangeRequired());
        return "account/password";
    }
    @PostMapping("/account/password")
    public ModelAndView change(@AuthenticationPrincipal SmartFixUserDetails principal,
            @RequestParam String currentPassword, @RequestParam String newPassword,
            @RequestParam String confirmPassword, Model model, HttpServletRequest request,
            HttpServletResponse response, Authentication authentication) {
        try {
            users.changeOwnPassword(principal.getUserId(), currentPassword, newPassword, confirmPassword);
        } catch (InputValidationException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("requiredChange", users.getUserAccess(principal.getUserId()).passwordChangeRequired());
            var result = new ModelAndView("account/password", model.asMap());
            result.setStatus(org.springframework.http.HttpStatus.BAD_REQUEST);
            return result;
        }
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return new ModelAndView("redirect:/login?passwordChanged");
    }
}
