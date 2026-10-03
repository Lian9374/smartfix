package com.smartfix.request.service;

import com.smartfix.common.exception.*;
import com.smartfix.request.config.RequestWorkflowProperties;
import com.smartfix.request.domain.*;
import com.smartfix.request.dto.RequestTransitionResponse;
import com.smartfix.request.event.RequestStatusChangedEvent;
import com.smartfix.request.repository.*;
import com.smartfix.request.spi.RequestTransitionParticipant;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.service.UserService;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class RequestLifecycleService {
    private final MaintenanceRequestRepository requests;
    private final RequestStatusHistoryRepository history;
    private final RequestAccessService access;
    private final UserService users;
    private final RequestAssignmentAccessService assignments;
    private final ObjectProvider<RequestTransitionParticipant> participants;
    private final ApplicationEventPublisher events;
    private final RequestWorkflowProperties properties;
    private final Clock clock;

    public RequestLifecycleService(
            MaintenanceRequestRepository requests,
            RequestStatusHistoryRepository history,
            RequestAccessService access,
            UserService users,
            RequestAssignmentAccessService assignments,
            ObjectProvider<RequestTransitionParticipant> participants,
            ApplicationEventPublisher events,
            RequestWorkflowProperties properties,
            Clock clock) {
        this.requests = requests;
        this.history = history;
        this.access = access;
        this.users = users;
        this.assignments = assignments;
        this.participants = participants;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public RequestTransitionResponse transition(
            String ticketNumber, RequestStatus target, Long actorUserId, String comment) {
        MaintenanceRequest request = access.requireReadableRequest(ticketNumber, actorUserId);
        var actor = users.getUserAccess(actorUserId);
        if (actor.accountStatus() != AccountStatus.ACTIVE)
            throw new ResourceNotFoundException("Request not found");
        if (target == null) throw new InputValidationException("A target state is required.");
        if (!RequestTransition.allows(request.getStatus(), target, actor.role())) {
            throw new BusinessConflictException(
                    "This action is not available in the current request state.");
        }
        String note = normalizeComment(comment);
        if ((target == RequestStatus.REJECTED || target == RequestStatus.RESOLVED)
                && (note == null || note.isBlank())) {
            throw new InputValidationException("A reason or solution is required.");
        }
        if (target == RequestStatus.ASSIGNED) {
            assignments.requireActiveAssignment(request.getId());
            if (request.getFinalUrgencyLevel() == null)
                throw new BusinessConflictException("Set the reviewed priority before dispatch.");
        }
        if (actor.role() == Role.TECHNICIAN
                && !assignments
                        .requireActiveAssignment(request.getId())
                        .technicianId()
                        .equals(actorUserId)) {
            throw new ResourceNotFoundException("Request not found");
        }
        Instant now = clock.instant();
        if (target == RequestStatus.REOPENED && !properties.getReopenWindow().isZero()) {
            Instant baseline =
                    request.getConfirmedAt() == null
                            ? request.getResolvedAt()
                            : request.getConfirmedAt();
            if (baseline == null || now.isAfter(baseline.plus(properties.getReopenWindow()))) {
                throw new BusinessConflictException("The reopen window has expired.");
            }
        }
        var change =
                new RequestTransitionResponse(
                        request.getId(),
                        request.getTicketNumber(),
                        request.getStatus(),
                        target,
                        actorUserId,
                        now);
        List<RequestTransitionParticipant> hooks = participants.orderedStream().toList();
        hooks.forEach(h -> h.beforeTransition(change));
        request.transitionTo(target, now);
        requests.saveAndFlush(
                request); // Detect stale versions before success is returned/published.
        history.save(
                RequestStatusHistory.transition(
                        request.getId(), change.fromStatus(), target, actorUserId, now, note));
        hooks.forEach(h -> h.afterTransition(change));
        events.publishEvent(
                new RequestStatusChangedEvent(
                        request.getId(),
                        ticketNumber,
                        request.getRequesterId(),
                        change.fromStatus(),
                        target,
                        actorUserId,
                        now));
        return change;
    }

    @Transactional
    public void recordInitialSubmission(Long requestId, Long actorUserId) {
        MaintenanceRequest request =
                requests.findById(requestId)
                        .orElseThrow(() -> new ResourceNotFoundException("Request not found"));
        var actor = users.getUserAccess(actorUserId);
        if (actor.role() != Role.REQUESTER
                || actor.accountStatus() != AccountStatus.ACTIVE
                || !request.getRequesterId().equals(actorUserId))
            throw new ResourceNotFoundException("Request not found");
        if (request.getStatus() != RequestStatus.SUBMITTED
                || history.existsByRequestId(requestId)) {
            throw new BusinessConflictException(
                    "Initial history is already recorded or the request has progressed.");
        }
        history.save(
                RequestStatusHistory.initialSubmission(
                        requestId, actorUserId, request.getCreatedAt()));
        events.publishEvent(
                new RequestStatusChangedEvent(
                        requestId,
                        request.getTicketNumber(),
                        actorUserId,
                        null,
                        RequestStatus.SUBMITTED,
                        actorUserId,
                        request.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public RequestStatus getStatus(String ticketNumber) {
        return requests.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found"))
                .getStatus();
    }

    private String normalizeComment(String value) {
        String result = value == null ? null : value.trim();
        if (result != null && result.length() > RequestStatusHistory.COMMENT_MAX_LENGTH) {
            throw new InputValidationException("Comment must be at most 500 characters.");
        }
        return result == null || result.isEmpty() ? null : result;
    }
}
