package com.smartfix.facility.controller;

import com.smartfix.facility.domain.FacilityStatus;
import com.smartfix.facility.service.FacilityService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.ui.Model;

@Controller
@RequestMapping("/admin/facilities")
public class FacilityController {

    private final FacilityService facilityService;

    public FacilityController(FacilityService facilityService) {
        this.facilityService = facilityService;
    }

    @GetMapping
    public String listFacilities(Model model) {
        model.addAttribute(
            "facilities",
            facilityService.listFacilities());

        model.addAttribute(
            "facilityStatuses",
            FacilityStatus.values());

        return "facility/list";
    }

    @PostMapping("/{facilityId}/status")
    public String changeStatus(
        @PathVariable Long facilityId,
        @RequestParam FacilityStatus status,
        RedirectAttributes redirectAttributes) {

        facilityService.changeStatus(facilityId, status);

        redirectAttributes.addFlashAttribute(
            "successMessage",
            "Facility status updated successfully.");

        return "redirect:/admin/facilities";
    }
}
