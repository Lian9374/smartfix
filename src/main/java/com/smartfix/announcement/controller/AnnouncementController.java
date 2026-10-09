package com.smartfix.announcement.controller;

import com.smartfix.announcement.dto.CreateAnnouncementCommand;
import com.smartfix.announcement.service.AnnouncementService;
import com.smartfix.auth.security.SmartFixUserDetails;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Controller
public class AnnouncementController {

    private static final ZoneId REPORTING_ZONE =
        ZoneId.of("Asia/Singapore");

    private final AnnouncementService announcements;

    public AnnouncementController(AnnouncementService announcements) {
        this.announcements = announcements;
    }

    @GetMapping("/announcements")
    public String publicList(Model model) {
        model.addAttribute("announcements", announcements.listVisible());
        return "announcement/list";
    }

    @GetMapping("/admin/announcements")
    public String adminList(Model model) {
        populateAdminModel(model);
        model.addAttribute("command", new CreateAnnouncementCommand());
        return "announcement/admin";
    }

    @PostMapping("/admin/announcements")
    public String create(
        @Valid @ModelAttribute("command") CreateAnnouncementCommand command,
        BindingResult errors,
        @AuthenticationPrincipal SmartFixUserDetails principal,
        Model model) {

        if (errors.hasErrors()) {
            populateAdminModel(model);
            return "announcement/admin";
        }

        Instant validFrom = toInstant(command.getValidFrom());

        Instant validTo = command.getValidTo() == null
            ? null
            : toInstant(command.getValidTo());

        announcements.createDraft(
            command.getTitle(),
            command.getContent(),
            validFrom,
            validTo,
            principal.getUserId());

        return "redirect:/admin/announcements";
    }

    @PostMapping("/admin/announcements/{id}/publish")
    public String publish(@PathVariable Long id) {
        announcements.publish(id);
        return "redirect:/admin/announcements";
    }

    @PostMapping("/admin/announcements/{id}/withdraw")
    public String withdraw(@PathVariable Long id) {
        announcements.withdraw(id);
        return "redirect:/admin/announcements";
    }

    private void populateAdminModel(Model model) {
        model.addAttribute("announcements", announcements.listAll());
    }

    private Instant toInstant(LocalDateTime value) {
        return value.atZone(REPORTING_ZONE).toInstant();
    }
}
