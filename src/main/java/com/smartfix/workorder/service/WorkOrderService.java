package com.smartfix.workorder.service;

import com.smartfix.common.exception.*;
import com.smartfix.request.domain.RequestStatus;
import com.smartfix.request.service.*;
import com.smartfix.user.domain.*;
import com.smartfix.user.service.UserService;
import com.smartfix.workorder.domain.*;
import com.smartfix.workorder.dto.*;
import com.smartfix.workorder.event.WorkOrderCompletedEvent;
import com.smartfix.workorder.repository.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class WorkOrderService {
    private final EntityManager entityManager;
    private final WorkOrderRepository orders;
    private final RepairRecordRepository records;
    private final RequestAssignmentAccessService assignments;
    private final RequestReadService requests;
    private final RequestLifecycleService lifecycle;
    private final RequestEvidenceService evidence;
    private final UserService users;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public WorkOrderService(
            WorkOrderRepository orders,
            RepairRecordRepository records,
            RequestAssignmentAccessService assignments,
            RequestReadService requests,
            RequestLifecycleService lifecycle,
            RequestEvidenceService evidence,
            UserService users,
            Clock clock,
            ApplicationEventPublisher events,
            EntityManager entityManager) {
        this.orders = orders;
        this.records = records;
        this.assignments = assignments;
        this.requests = requests;
        this.lifecycle = lifecycle;
        this.evidence = evidence;
        this.users = users;
        this.clock = clock;
        this.events = events;
        this.entityManager = entityManager;
    }

    /**
     * B calls this inside its assignment transaction; repeated calls for the same active assignment
     * are harmless.
     */
    @Transactional
    public WorkOrderResponse createFor(Long requestId, Long technicianId) {
        var a = assignments.requireActiveAssignment(requestId);
        if (!a.technicianId().equals(technicianId)
                || requests.findById(requestId).status() != RequestStatus.ASSIGNED) {
            throw new BusinessConflictException(
                    "Assign the request before creating the work order.");
        }
        WorkOrder w =
                orders.findByRequestId(requestId)
                        .orElseGet(
                                () -> WorkOrder.create(requestId, technicianId, clock.instant()));
        if (!w.getTechnicianId().equals(technicianId))
            throw new BusinessConflictException(
                    "Synchronize reassignment through the lifecycle first.");
        return response(orders.saveAndFlush(w));
    }

    public Optional<WorkOrderResponse> findByRequestId(Long requestId) {
        return orders.findByRequestId(requestId).map(this::response);
    }

    public Page<WorkOrderResponse> findMine(Long actorId, int page, int size) {
        requireTechnician(actorId);
        var pageable = PageRequest.of(
                Math.max(0, page), Math.max(1, Math.min(100, size)),
                Sort.by(Sort.Direction.DESC, "updatedAt", "id"));
        var activeRequestIds = assignments.findActiveRequestIds(
                actorId, orders.findRequestIdsByTechnicianId(actorId));
        if (activeRequestIds.isEmpty()) return Page.empty(pageable);
        // Filter before pagination so withdrawn assignments affect rows and totals equally.
        return orders.findByTechnicianIdAndRequestIdIn(actorId, activeRequestIds, pageable)
                .map(this::response);
    }

    public WorkOrderResponse findReadable(Long id, Long actorId) {
        return response(requireAssigned(id, actorId));
    }

    public List<RepairRecordResponse> findRecords(Long id, Long actorId) {
        requireAssigned(id, actorId);
        return records.findByWorkOrderIdOrderByCreatedAtAscIdAsc(id).stream()
                .map(
                        r ->
                                new RepairRecordResponse(
                                        r.getId(),
                                        r.getTechnicianId(),
                                        r.getDiagnosis(),
                                        r.getActionTaken(),
                                        r.getMaterialsUsed(),
                                        r.getMinutesSpent(),
                                        r.getEvidenceAttachmentId(),
                                        r.getCreatedAt()))
                .toList();
    }

    public long countOpenWorkOrders(Long technicianId) {
        return orders
                .findAllByTechnicianIdAndStatusIn(
                        technicianId,
                        List.of(
                                WorkOrderStatus.CREATED,
                                WorkOrderStatus.IN_PROGRESS,
                                WorkOrderStatus.ON_HOLD,
                                WorkOrderStatus.REOPENED))
                .stream()
                .filter(w -> assignments.isAssignedTo(w.getRequestId(), technicianId))
                .count();
    }

    @Transactional
    public void accept(Long id, Long actorId) {
        WorkOrder w = requireAssigned(id, actorId);
        lifecycle.transition(
                requests.findById(w.getRequestId()).ticketNumber(),
                RequestStatus.IN_PROGRESS,
                actorId,
                null);
    }

    @Transactional
    public void recordRepair(Long id, Long actorId, RepairRecordCommand command) {
        WorkOrder w = requireAssigned(id, actorId);
        requireInProgress(w);
        var record = RepairRecord.create(id, actorId, command, clock.instant());
        if (command.getEvidenceAttachmentId() != null)
            evidence.requireBelongsToRequest(command.getEvidenceAttachmentId(), w.getRequestId());
        entityManager.lock(w, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        w.touch(clock.instant());
        orders.saveAndFlush(w); // stale writes lose against concurrent reassignment/completion
        records.save(record);
    }

    @Transactional
    public void complete(Long id, Long actorId, CompleteWorkOrderCommand command) {
        WorkOrder w = requireAssigned(id, actorId);
        requireInProgress(w);
        if (command == null) throw new InputValidationException("A solution is required.");
        if (!records.existsByWorkOrderId(id))
            throw new BusinessConflictException(
                    "Add a repair record before completing the work order.");
        var r = requests.findById(w.getRequestId());
        w.complete(command.getResolutionNote(), clock.instant());
        orders.saveAndFlush(w);
        var change =
                lifecycle.transition(
                        r.ticketNumber(), RequestStatus.RESOLVED, actorId, w.getResolutionNote());
        events.publishEvent(
                new WorkOrderCompletedEvent(
                        id,
                        r.id(),
                        r.ticketNumber(),
                        actorId,
                        r.requesterId(),
                        change.occurredAt()));
    }

    private WorkOrder requireAssigned(Long id, Long actorId) {
        requireTechnician(actorId);
        WorkOrder w =
                orders.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Work order not found"));
        if (!w.getTechnicianId().equals(actorId)
                || !assignments.isAssignedTo(w.getRequestId(), actorId)) {
            throw new ResourceNotFoundException("Work order not found");
        }
        return w;
    }

    private void requireTechnician(Long actorId) {
        var actor = users.getUserAccess(actorId);
        if (actor.role() != Role.TECHNICIAN || actor.accountStatus() != AccountStatus.ACTIVE) {
            throw new ResourceNotFoundException("Work order not found");
        }
    }

    private void requireInProgress(WorkOrder w) {
        if (requests.findById(w.getRequestId()).status() != RequestStatus.IN_PROGRESS
                || (w.getStatus() != WorkOrderStatus.IN_PROGRESS
                        && w.getStatus() != WorkOrderStatus.ON_HOLD)) {
            throw new BusinessConflictException(
                    "Start the work order before recording or completing repairs.");
        }
    }

    private WorkOrderResponse response(WorkOrder w) {
        return new WorkOrderResponse(
                w.getId(),
                w.getRequestId(),
                requests.findById(w.getRequestId()).ticketNumber(),
                w.getTechnicianId(),
                w.getStatus(),
                w.getResolutionNote(),
                w.getCreatedAt(),
                w.getCompletedAt());
    }
}
