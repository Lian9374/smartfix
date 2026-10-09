package com.smartfix.workorder.controller;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.facility.service.LocationService;
import com.smartfix.request.service.AttachmentService;
import com.smartfix.request.service.RequestQueryService;
import com.smartfix.workorder.domain.WorkOrderStatus;
import com.smartfix.workorder.dto.*;
import com.smartfix.workorder.service.WorkOrderService;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
public class WorkOrderController {
    private final WorkOrderService orders;

    private final AttachmentService attachments;
    private final LocationService locations;
    private final RequestQueryService requests;

    public WorkOrderController(WorkOrderService orders, AttachmentService attachments,
            LocationService locations, RequestQueryService requests) {
        this.orders = orders;
        this.attachments = attachments;
        this.locations = locations;
        this.requests = requests;
    }

    @GetMapping({"/workorders/mine", "/technician"})
    public String mine(
            @AuthenticationPrincipal SmartFixUserDetails p,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(required = false) com.smartfix.request.domain.UrgencyLevel priority,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate since,
            @RequestParam(defaultValue = "newest") String sort,
            Model model) {
        var result = orders.queue(p.getUserId(), status, priority, since, sort, page, size);
        model.addAttribute("orders", result);
        var locationNames = new java.util.HashMap<Long, String>();
        result.getContent().forEach(row -> locationNames.computeIfAbsent(row.locationId(), id -> locations.getLocation(id).displayName()));
        model.addAttribute("locationNames", locationNames);
        model.addAttribute("statuses", WorkOrderStatus.values());
        model.addAttribute("priorities", com.smartfix.request.domain.UrgencyLevel.values());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedPriority", priority);
        model.addAttribute("since", since);
        model.addAttribute("selectedSort", sort);
        return "workorder/mine";
    }

    @GetMapping("/workorders/{id}")
    public String detail(
            @PathVariable Long id, @AuthenticationPrincipal SmartFixUserDetails p, Model model) {
        model.addAttribute("repair", new RepairRecordCommand());
        model.addAttribute("completion", new CompleteWorkOrderCommand());
        populate(id, p.getUserId(), model);
        return "workorder/detail";
    }

    @PostMapping("/workorders/{id}/accept")
    public String accept(@PathVariable Long id, @AuthenticationPrincipal SmartFixUserDetails p,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        orders.accept(id, p.getUserId());
        redirect.addFlashAttribute("successMessage", "Work started. Add repair records as you investigate.");
        return "redirect:/workorders/" + id;
    }

    @PostMapping("/workorders/{id}/records")
    public String record(
            @PathVariable Long id,
            @Valid @ModelAttribute("repair") RepairRecordCommand c,
            BindingResult errors,
            @AuthenticationPrincipal SmartFixUserDetails p,
            Model model, org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                orders.recordRepair(id, p.getUserId(), c);
                redirect.addFlashAttribute("successMessage", "Repair record saved.");
                return "redirect:/workorders/" + id;
            } catch (com.smartfix.common.exception.BusinessConflictException | com.smartfix.common.exception.InputValidationException ex) {
                errors.reject("repair.refused", ex.getMessage());
            }
        }
        model.addAttribute("completion", new CompleteWorkOrderCommand());
        populate(id, p.getUserId(), model);
        return "workorder/detail";
    }

    @PostMapping("/workorders/{id}/complete")
    public String complete(
            @PathVariable Long id,
            @Valid @ModelAttribute("completion") CompleteWorkOrderCommand c,
            BindingResult errors,
            @AuthenticationPrincipal SmartFixUserDetails p,
            Model model, org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                orders.complete(id, p.getUserId(), c);
                redirect.addFlashAttribute("successMessage", "Solution submitted. The requester can now confirm the repair.");
                return "redirect:/workorders/" + id;
            } catch (com.smartfix.common.exception.BusinessConflictException | com.smartfix.common.exception.InputValidationException ex) {
                errors.reject("completion.refused", ex.getMessage());
            }
        }
        model.addAttribute("repair", new RepairRecordCommand());
        populate(id, p.getUserId(), model);
        return "workorder/detail";
    }

    private void populate(Long id, Long actorId, Model model) {
        var order = orders.findReadable(id, actorId);
        model.addAttribute("order", order);
        model.addAttribute("request", requests.getRequestDetails(order.ticketNumber(), actorId));
        model.addAttribute("evidence", attachments.findByRequestId(order.requestId()));
        model.addAttribute("records", orders.findRecords(id, actorId));
    }
}
