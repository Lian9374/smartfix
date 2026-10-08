package com.smartfix.workorder.controller;

import com.smartfix.auth.security.SmartFixUserDetails;
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
    private final RequestQueryService requests;

    public WorkOrderController(WorkOrderService orders, AttachmentService attachments,
            RequestQueryService requests) {
        this.orders = orders;
        this.attachments = attachments;
        this.requests = requests;
    }

    @GetMapping("/workorders/mine")
    public String mine(
            @AuthenticationPrincipal SmartFixUserDetails p,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {
        model.addAttribute("orders", orders.findMine(p.getUserId(), page, size));
        model.addAttribute("statuses", WorkOrderStatus.values());
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
    public String accept(@PathVariable Long id, @AuthenticationPrincipal SmartFixUserDetails p) {
        orders.accept(id, p.getUserId());
        return "redirect:/workorders/" + id;
    }

    @PostMapping("/workorders/{id}/records")
    public String record(
            @PathVariable Long id,
            @Valid @ModelAttribute("repair") RepairRecordCommand c,
            BindingResult errors,
            @AuthenticationPrincipal SmartFixUserDetails p,
            Model model) {
        if (!errors.hasErrors()) {
            orders.recordRepair(id, p.getUserId(), c);
            return "redirect:/workorders/" + id;
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
            Model model) {
        if (!errors.hasErrors()) {
            orders.complete(id, p.getUserId(), c);
            return "redirect:/workorders/" + id;
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
