package com.smartfix.audit.controller;

import com.smartfix.audit.service.AuditService;
import com.smartfix.auth.security.SmartFixUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuditController {
    private final AuditService audit;
    public AuditController(AuditService audit) { this.audit = audit; }

    @GetMapping("/admin/community/audit")
    public String list(@AuthenticationPrincipal SmartFixUserDetails principal,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size, Model model) {
        var found = audit.list(principal.getUserId(), page, size);
        model.addAttribute("entries", found.getContent());
        model.addAttribute("pagination", found);
        return "admin/community-audit";
    }
}
