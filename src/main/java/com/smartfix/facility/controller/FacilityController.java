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
    private final com.smartfix.facility.service.LocationService locations;

    public FacilityController(FacilityService facilityService, com.smartfix.facility.service.LocationService locations) {
        this.facilityService = facilityService;
        this.locations = locations;
    }

    @GetMapping
    public String listFacilities(Model model) {
        model.addAttribute("createFacility", new com.smartfix.facility.dto.CreateFacilityCommand(null, "", "", FacilityStatus.OPERATIONAL));
        return populate(model);
    }

    private String populate(Model model) {
        model.addAttribute("locations", locations.listActiveLocations());
        model.addAttribute(
            "facilities",
            facilityService.listFacilities());

        model.addAttribute(
            "facilityStatuses",
            FacilityStatus.values());

        return "facility/list";
    }

    @PostMapping
    public String createFacility(
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.ModelAttribute("createFacility")
            com.smartfix.facility.dto.CreateFacilityCommand command,
            org.springframework.validation.BindingResult errors, Model model,
            jakarta.servlet.http.HttpServletResponse response, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                facilityService.createFacility(command);
                redirect.addFlashAttribute("successMessage", "Facility added. Its status is now available on the campus map.");
                return "redirect:/admin/facilities";
            } catch (com.smartfix.common.exception.InputValidationException ex) {
                errors.reject("facility.invalid", ex.getMessage());
            }
        }
        model.addAttribute("facilityFormInvalid", true);
        response.setStatus(400);
        return populate(model);
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
