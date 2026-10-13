package com.smartfix.request.controller;
import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.request.service.AttachmentInspectionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
@Controller
public class AttachmentInspectionController{
    private final AttachmentInspectionService service;
    public AttachmentInspectionController(AttachmentInspectionService service){this.service=service;}
    @GetMapping("/admin/attachments/inspection")
    public String inspect(@AuthenticationPrincipal SmartFixUserDetails actor,Model model){model.addAttribute("inspection",service.inspect(actor.getUserId()));return "request/attachment-inspection";}
}
