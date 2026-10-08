package com.smartfix.dispatch.controller;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.common.exception.BusinessConflictException;
import com.smartfix.common.exception.InputValidationException;
import com.smartfix.dispatch.dto.*;
import com.smartfix.dispatch.service.AssignmentService;
import com.smartfix.dispatch.service.DispatchPageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/requests/{ticketNumber}")
public class DispatchController {
    private final AssignmentService assignments;
    private final DispatchPageService pages;

    public DispatchController(AssignmentService assignments, DispatchPageService pages) {
        this.assignments = assignments;
        this.pages = pages;
    }

    @InitBinder({"assignCommand", "reassignCommand", "withdrawCommand"})
    void bindCommandOnly(WebDataBinder binder) {
        binder.setAllowedFields("technicianId", "expectedAssignmentId", "reason");
    }

    @GetMapping("/dispatch")
    public ModelAndView dispatch(@PathVariable String ticketNumber,
            @AuthenticationPrincipal SmartFixUserDetails principal, Model model) {
        return render(ticketNumber, principal.getUserId(), model, HttpStatus.OK);
    }

    @PostMapping("/assign")
    public ModelAndView assign(@PathVariable String ticketNumber,
            @Valid @ModelAttribute("assignCommand") AssignTechnicianCommand command, BindingResult errors,
            @AuthenticationPrincipal SmartFixUserDetails principal, Model model, RedirectAttributes redirect) {
        return submit(ticketNumber, principal.getUserId(), errors, model, redirect,
                () -> assignments.assign(ticketNumber, command, principal.getUserId()), "Technician assigned.");
    }

    @PostMapping("/reassign")
    public ModelAndView reassign(@PathVariable String ticketNumber,
            @Valid @ModelAttribute("reassignCommand") ReassignTechnicianCommand command, BindingResult errors,
            @AuthenticationPrincipal SmartFixUserDetails principal, Model model, RedirectAttributes redirect) {
        return submit(ticketNumber, principal.getUserId(), errors, model, redirect,
                () -> assignments.reassign(ticketNumber, command, principal.getUserId()), "Technician reassigned.");
    }

    @PostMapping("/withdraw")
    public ModelAndView withdraw(@PathVariable String ticketNumber,
            @Valid @ModelAttribute("withdrawCommand") WithdrawAssignmentCommand command, BindingResult errors,
            @AuthenticationPrincipal SmartFixUserDetails principal, Model model, RedirectAttributes redirect) {
        return submit(ticketNumber, principal.getUserId(), errors, model, redirect,
                () -> assignments.withdraw(ticketNumber, command, principal.getUserId()), "Assignment withdrawn. The request is ready for review.");
    }

    private ModelAndView submit(String ticket, Long actorId, BindingResult errors, Model model,
            RedirectAttributes redirect, Runnable action, String success) {
        if (errors.hasErrors()) {
            model.addAttribute("errorMessage", "Check the selected technician and current assignment. A reason of 1–500 characters is required for reassignment or withdrawal.");
            return render(ticket, actorId, model, HttpStatus.BAD_REQUEST);
        }
        try {
            action.run();
        } catch (InputValidationException ex) {
            model.addAttribute("errorMessage", "Check your selections and reason, then reload if the request or location has changed.");
            return render(ticket, actorId, model, HttpStatus.BAD_REQUEST);
        } catch (BusinessConflictException ex) {
            // Never expose SQL/exception details or silently replace a stale form's assignment id.
            model.addAttribute("errorMessage", "The request, assignment or technician availability has changed. Reload the page, review the current details and try again.");
            model.addAttribute("conflict", true);
            return render(ticket, actorId, model, HttpStatus.CONFLICT);
        }
        redirect.addFlashAttribute("successMessage", success);
        return new ModelAndView("redirect:/admin/requests/{ticketNumber}/dispatch", "ticketNumber", ticket);
    }

    private ModelAndView render(String ticket, Long actorId, Model model, HttpStatus status) {
        var page = pages.describe(ticket, actorId);
        model.addAttribute("page", page);
        if (!model.containsAttribute("conflict")) model.addAttribute("conflict", false);
        Long expectedId = page.currentAssignment() == null ? null : page.currentAssignment().id();
        if (!model.containsAttribute("assignCommand")) model.addAttribute("assignCommand", new AssignTechnicianCommand(null));
        if (!model.containsAttribute("reassignCommand")) model.addAttribute("reassignCommand", new ReassignTechnicianCommand(null, expectedId, ""));
        if (!model.containsAttribute("withdrawCommand")) model.addAttribute("withdrawCommand", new WithdrawAssignmentCommand(expectedId, ""));
        var result = new ModelAndView("dispatch/assign", model.asMap());
        result.setStatus(status);
        return result;
    }
}
