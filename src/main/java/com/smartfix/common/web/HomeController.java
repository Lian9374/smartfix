package com.smartfix.common.web;

import com.smartfix.auth.security.SmartFixUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Authenticated landing page. Technicians get a controlled placeholder this sprint.
 */
@Controller
public class HomeController {

    private static final String VIEW_HOME = "home";

    @GetMapping({"/", "/home"})
    public String home(@AuthenticationPrincipal SmartFixUserDetails principal, Model model) {
        model.addAttribute("systemName", "SmartFix");
        model.addAttribute("tagline", "Campus Facility Maintenance and Technician Dispatch System");
        model.addAttribute("username", principal.getUsername());
        model.addAttribute("role", principal.getRole().name());
        return VIEW_HOME;
    }
}
