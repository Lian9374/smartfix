package com.smartfix.workorder.service;

import com.smartfix.common.exception.BusinessConflictException;
import com.smartfix.request.domain.RequestStatus;
import com.smartfix.request.dto.RequestTransitionResponse;
import com.smartfix.request.service.RequestAssignmentAccessService;
import com.smartfix.request.spi.RequestTransitionParticipant;
import com.smartfix.workorder.domain.*;
import com.smartfix.workorder.repository.WorkOrderRepository;

import org.springframework.stereotype.Component;

/** Does not depend on WorkOrderService/lifecycle: avoids a service dependency cycle. */
@Component
public class WorkOrderLifecycleParticipant implements RequestTransitionParticipant {
    private final WorkOrderRepository orders;
    private final RequestAssignmentAccessService assignments;

    public WorkOrderLifecycleParticipant(
            WorkOrderRepository orders, RequestAssignmentAccessService assignments) {
        this.orders = orders;
        this.assignments = assignments;
    }

    @Override
    public void beforeTransition(RequestTransitionResponse c) {
        var order = orders.findByRequestId(c.requestId());
        if (c.toStatus() == RequestStatus.IN_PROGRESS || c.toStatus() == RequestStatus.RESOLVED) {
            WorkOrder w =
                    order.orElseThrow(
                            () ->
                                    new BusinessConflictException(
                                            "The work order is not available."));
            var assignment = assignments.requireActiveAssignment(c.requestId());
            if (!w.getTechnicianId().equals(c.actorUserId())
                    || !assignment.technicianId().equals(c.actorUserId())) {
                throw new BusinessConflictException("The assignment has changed.");
            }
            if (c.toStatus() == RequestStatus.RESOLVED
                    && (w.getStatus() != WorkOrderStatus.COMPLETED
                            || w.getResolutionNote() == null
                            || w.getCompletedAt() == null)) {
                throw new BusinessConflictException(
                        "Complete the work order and provide a solution first.");
            }
            if (c.toStatus() == RequestStatus.IN_PROGRESS
                    && w.getStatus() != WorkOrderStatus.CREATED
                    && w.getStatus() != WorkOrderStatus.REOPENED) {
                throw new BusinessConflictException(
                        "The work order has already started or is unavailable.");
            }
        }
        if (c.toStatus() == RequestStatus.REOPENED
                || c.toStatus() == RequestStatus.CONFIRMED
                || c.toStatus() == RequestStatus.CLOSED) {
            WorkOrder w =
                    order.orElseThrow(
                            () ->
                                    new BusinessConflictException(
                                            "The work order is not available."));
            if (w.getStatus() != WorkOrderStatus.COMPLETED)
                throw new BusinessConflictException("The work order has not been completed.");
        }
        if (c.fromStatus() == RequestStatus.ASSIGNED
                && c.toStatus() == RequestStatus.UNDER_REVIEW
                && assignments.findActiveAssignment(c.requestId()).isPresent()) {
            throw new BusinessConflictException(
                    "Withdraw the active assignment before returning to review.");
        }
    }

    @Override
    public void afterTransition(RequestTransitionResponse c) {
        if (c.toStatus() == RequestStatus.ASSIGNED) {
            var a = assignments.requireActiveAssignment(c.requestId());
            var w =
                    orders.findByRequestId(c.requestId())
                            .orElseGet(
                                    () ->
                                            WorkOrder.create(
                                                    c.requestId(),
                                                    a.technicianId(),
                                                    c.occurredAt()));
            w.assignTo(a.technicianId(), c.occurredAt());
            orders.saveAndFlush(w);
            return;
        }
        orders.findByRequestId(c.requestId())
                .ifPresent(
                        w -> {
                            switch (c.toStatus()) {
                                case IN_PROGRESS ->
                                        w.syncStatus(WorkOrderStatus.IN_PROGRESS, c.occurredAt());
                                case REOPENED -> w.reopen(c.occurredAt());
                                case CLOSED, CANCELLED, REJECTED ->
                                        w.syncStatus(WorkOrderStatus.CLOSED, c.occurredAt());
                                case UNDER_REVIEW ->
                                        w.syncStatus(WorkOrderStatus.ON_HOLD, c.occurredAt());
                                default -> {
                                    return;
                                }
                            }
                            orders.saveAndFlush(w);
                        });
    }
}
