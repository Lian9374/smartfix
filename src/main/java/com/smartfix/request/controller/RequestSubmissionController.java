package com.smartfix.request.controller;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.common.exception.InputValidationException;
import com.smartfix.facility.service.LocationService;
import com.smartfix.request.domain.*;
import com.smartfix.request.dto.SubmitMaintenanceRequestCommand;
import com.smartfix.request.service.RequestSubmissionService;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Controller
public class RequestSubmissionController {
    private final RequestSubmissionService submissions;
    private final LocationService locations;

    public RequestSubmissionController(
            RequestSubmissionService submissions, LocationService locations) {
        this.submissions = submissions;
        this.locations = locations;
    }

    @ModelAttribute("categories")
    public MaintenanceCategory[] categories() {
        return MaintenanceCategory.values();
    }

    @ModelAttribute("urgencyLevels")
    public UrgencyLevel[] urgencyLevels() {
        return UrgencyLevel.values();
    }

    @GetMapping("/requests/new")
    public String form(Model model) {
        model.addAttribute("command", new SubmitMaintenanceRequestCommand());
        model.addAttribute("locations", locations.listActiveLocations());
        return "request/new";
    }

    @PostMapping("/requests")
    public String submit(
            @Valid @ModelAttribute("command") SubmitMaintenanceRequestCommand command,
            BindingResult errors,
            @RequestParam(required = false) List<MultipartFile> files,
            @AuthenticationPrincipal SmartFixUserDetails principal,
            Model model) {
        if (!errors.hasErrors()) {
            try {
                var result = submissions.submit(command, files, principal.getUserId());
                return "redirect:/requests/" + result.ticketNumber();
            } catch (InputValidationException invalid) {
                errors.reject(
                        "request.invalid",
                        "Check the form and attachments. Images must meet the upload limits.");
            }
        }
        model.addAttribute("locations", locations.listActiveLocations());
        return "request/new";
    }
}
