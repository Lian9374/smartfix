package com.smartfix.common.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Minimal controller that renders the SmartFix scaffold home page.
 *
 * <p>This exists only to verify that the engineering foundation works end to end
 * (web layer -> view template -> HTTP). It contains no business logic and is not a
 * dashboard or a preview of any SmartFix business function.</p>
 */
@Controller
public class HomeController {

    private static final String VIEW_HOME = "home";

    @GetMapping({"/", "/home"})
    public String home(Model model) {
        model.addAttribute("systemName", "SmartFix");
        model.addAttribute("tagline", "Campus Facility Maintenance and Technician Dispatch System");
        model.addAttribute("scaffoldStatus", "Initial Project Scaffold");
        return VIEW_HOME;
    }
}
