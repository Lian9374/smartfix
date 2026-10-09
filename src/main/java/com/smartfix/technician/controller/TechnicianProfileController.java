package com.smartfix.technician.controller;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.common.exception.BusinessConflictException;
import com.smartfix.common.exception.InputValidationException;
import com.smartfix.facility.service.LocationService;
import com.smartfix.request.domain.MaintenanceCategory;
import com.smartfix.technician.domain.AvailabilityStatus;
import com.smartfix.technician.dto.UpdateTechnicianProfileCommand;
import com.smartfix.technician.service.TechnicianDirectoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/technician/profile")
public class TechnicianProfileController {
    private static final String PROFILE_ACTIVE = "profileActive";
    private final TechnicianDirectoryService directory;
    private final LocationService locations;

    public TechnicianProfileController(TechnicianDirectoryService directory, LocationService locations) {
        this.directory = directory;
        this.locations = locations;
    }

    @InitBinder("profileCommand")
    void bindPreferencesOnly(WebDataBinder binder) {
        binder.setAllowedFields("skills", "serviceAreaIds", "availabilityStatus", "version");
    }

    @GetMapping
    public String profile(@AuthenticationPrincipal SmartFixUserDetails principal, Model model) {
        UpdateTechnicianProfileCommand command = new UpdateTechnicianProfileCommand();
        model.addAttribute(PROFILE_ACTIVE, true);
        directory.getProfile(principal.getUserId()).ifPresent(profile -> {
            command.setSkills(profile.skills());
            command.setServiceAreaIds(profile.serviceAreaIds());
            command.setAvailabilityStatus(profile.availabilityStatus());
            command.setVersion(profile.version());
            model.addAttribute(PROFILE_ACTIVE, profile.active());
        });
        model.addAttribute("profileCommand", command);
        populateOptions(model, command);
        return "technician/profile";
    }

    @PostMapping
    public ModelAndView save(@Valid @ModelAttribute("profileCommand") UpdateTechnicianProfileCommand command,
            BindingResult errors, @AuthenticationPrincipal SmartFixUserDetails principal,
            Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            return render(model, command, HttpStatus.BAD_REQUEST);
        }
        try {
            directory.updateProfile(principal.getUserId(), command);
        } catch (InputValidationException ex) {
            errors.reject("invalidProfile", "Select valid skills, active service areas and availability.");
            return render(model, command, HttpStatus.BAD_REQUEST);
        } catch (BusinessConflictException ex) {
            errors.reject("profileConflict", "Your profile changed. Reload the page before saving again.");
            return render(model, command, HttpStatus.CONFLICT);
        }
        redirect.addFlashAttribute("successMessage", "Your technician profile was saved.");
        return new ModelAndView("redirect:/technician/profile");
    }

    private ModelAndView render(Model model, UpdateTechnicianProfileCommand command, HttpStatus status) {
        model.addAttribute(PROFILE_ACTIVE, true);
        populateOptions(model, command);
        ModelAndView result = new ModelAndView("technician/profile", model.asMap());
        result.setStatus(status);
        return result;
    }

    private void populateOptions(Model model, UpdateTechnicianProfileCommand command) {
        var activeLocations = locations.listActiveLocations();
        model.addAttribute("locations", activeLocations);
        model.addAttribute("skills", MaintenanceCategory.values());
        model.addAttribute("skillLabels", Map.of(
                MaintenanceCategory.ELECTRICAL.name(), "Electrical", MaintenanceCategory.PLUMBING.name(), "Plumbing",
                MaintenanceCategory.HVAC.name(), "Heating, ventilation and air conditioning",
                MaintenanceCategory.BUILDING.name(), "Building", MaintenanceCategory.OTHER.name(), "Other"));
        model.addAttribute("availabilities", AvailabilityStatus.values());
        model.addAttribute("availabilityLabels", Map.of(AvailabilityStatus.AVAILABLE.name(), "Available",
                AvailabilityStatus.BUSY.name(), "Busy", AvailabilityStatus.ON_LEAVE.name(), "On leave"));
        model.addAttribute("hasUnavailableAreas", command.getServiceAreaIds() != null
                && command.getServiceAreaIds().stream().anyMatch(id ->
                activeLocations.stream().noneMatch(location -> location.id().equals(id))));
    }
}
