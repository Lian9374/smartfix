package com.smartfix.sla.controller;
import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.request.domain.UrgencyLevel;
import com.smartfix.sla.domain.SlaPhase;
import com.smartfix.sla.service.SlaPolicyService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller @RequestMapping("/admin/sla")
public class SlaController {
    private final SlaPolicyService service;
    public SlaController(SlaPolicyService service){this.service=service;}
    @GetMapping public String list(@AuthenticationPrincipal SmartFixUserDetails actor,@RequestParam(defaultValue="0") int page,Model model){var policies=service.policies(actor.getUserId());model.addAttribute("policies",policies);model.addAttribute("slaAvailable",policies.stream().anyMatch(com.smartfix.sla.domain.SlaPolicy::isEnabled));model.addAttribute("states",service.states(actor.getUserId(),page));model.addAttribute("urgencies",UrgencyLevel.values());model.addAttribute("phases",SlaPhase.values());return "sla/admin";}
    @PostMapping("/policies") public String configure(@AuthenticationPrincipal SmartFixUserDetails actor,@RequestParam UrgencyLevel urgency,@RequestParam SlaPhase phase,@RequestParam long minutes,@RequestParam long reminderMinutes,@RequestParam(defaultValue="false") boolean enabled,RedirectAttributes feedback){service.configure(actor.getUserId(),urgency,phase,minutes,reminderMinutes,enabled);feedback.addFlashAttribute("successMessage","Policy saved. Existing timers retain their duration snapshot; the policy applies to new timers or reopened resolution cycles.");return "redirect:/admin/sla";}
    @GetMapping("/requests/{id}") @ResponseBody public SlaPolicyService.ReadResult read(@AuthenticationPrincipal SmartFixUserDetails actor,@PathVariable Long id){return service.read(actor.getUserId(),id);}
}
