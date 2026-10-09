package com.smartfix.technician.controller;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.technician.service.TechnicianDirectoryService;
import com.smartfix.user.domain.Role;
import com.smartfix.user.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
public class AdminTechnicianController {
    private final UserService users;
    private final TechnicianDirectoryService technicians;
    public AdminTechnicianController(UserService users, TechnicianDirectoryService technicians) {
        this.users = users; this.technicians = technicians;
    }
    @GetMapping("/admin/technicians")
    public String list(@AuthenticationPrincipal SmartFixUserDetails principal, Model model) {
        model.addAttribute("profiles", technicians.listProfilesForAdministrator(principal.getUserId()).stream()
                .collect(Collectors.toMap(com.smartfix.technician.dto.TechnicianProfileResponse::userId, Function.identity())));
        model.addAttribute("technicians", users.listUsers().stream().filter(u -> u.role() == Role.TECHNICIAN).toList());
        return "admin/technicians";
    }
}
