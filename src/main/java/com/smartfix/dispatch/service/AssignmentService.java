package com.smartfix.dispatch.service;

import com.smartfix.common.exception.BusinessConflictException;
import com.smartfix.common.exception.InputValidationException;
import com.smartfix.dispatch.domain.Assignment;
import com.smartfix.dispatch.dto.*;
import com.smartfix.dispatch.event.*;
import com.smartfix.dispatch.repository.AssignmentRepository;
import com.smartfix.request.domain.RequestStatus;
import com.smartfix.request.dto.RequestSnapshotResponse;
import com.smartfix.request.service.RequestLifecycleService;
import com.smartfix.request.service.RequestReadService;
import com.smartfix.technician.service.TechnicianDirectoryService;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.service.UserService;
import jakarta.validation.Validator;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/** Owns the transaction spanning assignment, C's lifecycle/history, and C's work-order participant. */
@Service
@Transactional
public class AssignmentService {
    private final AssignmentRepository assignments;
    private final AssignmentReadService reads;
    private final RequestReadService requests;
    private final RequestLifecycleService lifecycle;
    private final TechnicianDirectoryService directory;
    private final UserService users;
    private final Validator validator;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AssignmentService(AssignmentRepository assignments, AssignmentReadService reads,
            RequestReadService requests, RequestLifecycleService lifecycle, TechnicianDirectoryService directory,
            UserService users, Validator validator, ApplicationEventPublisher events, Clock clock) {
        this.assignments = assignments;
        this.reads = reads;
        this.requests = requests;
        this.lifecycle = lifecycle;
        this.directory = directory;
        this.users = users;
        this.validator = validator;
        this.events = events;
        this.clock = clock;
    }

    public AssignmentResponse assign(String ticketNumber, AssignTechnicianCommand command, Long actorUserId) {
        requireAdministrator(actorUserId);
        validate(command);
        var request = request(ticketNumber);
        if (request.status() != RequestStatus.UNDER_REVIEW) {
            throw new BusinessConflictException("Only a request under review can be assigned.");
        }
        if (assignments.findByRequestIdAndActiveTrue(request.id()).isPresent()) {
            throw duplicateAssignment();
        }
        requireEligible(request, command.technicianId());
        try {
            return createAndTransition(request, command.technicianId(), actorUserId, null, null);
        } catch (OptimisticLockingFailureException ex) {
            throw staleAssignment();
        }
    }

    public AssignmentResponse reassign(String ticketNumber, ReassignTechnicianCommand command, Long actorUserId) {
        requireAdministrator(actorUserId);
        validate(command);
        var request = request(ticketNumber);
        if (request.status() != RequestStatus.ASSIGNED && request.status() != RequestStatus.IN_PROGRESS
                && request.status() != RequestStatus.REOPENED) {
            throw new BusinessConflictException("This request cannot be reassigned in its current state.");
        }
        var previous = requireCurrent(request.id(), command.expectedAssignmentId());
        // REOPENED may retain the original technician after eligibility is checked again (T12).
        if (previous.getTechnicianId().equals(command.technicianId()) && request.status() != RequestStatus.REOPENED) {
            throw new BusinessConflictException("Select a different technician for reassignment.");
        }
        requireEligible(request, command.technicianId());
        String reason = command.reason().trim();
        try {
            deactivate(previous, actorUserId, reason);
            if (request.status() == RequestStatus.ASSIGNED) {
                // C's T06 then T03; intermediate state is part of this same transaction.
                lifecycle.transition(ticketNumber, RequestStatus.UNDER_REVIEW, actorUserId, reason);
            }
            return createAndTransition(request, command.technicianId(), actorUserId, reason,
                    previous.getTechnicianId());
        } catch (OptimisticLockingFailureException ex) {
            throw staleAssignment();
        }
    }

    public AssignmentResponse withdraw(String ticketNumber, WithdrawAssignmentCommand command, Long actorUserId) {
        requireAdministrator(actorUserId);
        validate(command);
        var request = request(ticketNumber);
        if (request.status() != RequestStatus.ASSIGNED) {
            throw new BusinessConflictException("Only an assignment that has not started can be withdrawn.");
        }
        var current = requireCurrent(request.id(), command.expectedAssignmentId());
        String reason = command.reason().trim();
        try {
            deactivate(current, actorUserId, reason);
            lifecycle.transition(ticketNumber, RequestStatus.UNDER_REVIEW, actorUserId, reason);
            events.publishEvent(new AssignmentWithdrawnEvent(current.getId(), request.id(), ticketNumber,
                    request.requesterId(), current.getTechnicianId(), actorUserId, reason, current.getDeactivatedAt()));
            return AssignmentResponse.from(current);
        } catch (OptimisticLockingFailureException ex) {
            throw staleAssignment();
        }
    }

    /** Internal read API; C's adapter uses AssignmentReadService directly to avoid a dependency cycle. */
    @Transactional(readOnly = true)
    public Optional<AssignmentResponse> findActiveAssignment(Long requestId) {
        return reads.findActiveAssignment(requestId);
    }

    private AssignmentResponse createAndTransition(RequestSnapshotResponse request, Long technicianId,
            Long actorUserId, String reason, Long previousTechnicianId) {
        var assignment = Assignment.create(request.id(), technicianId, actorUserId, reason, now());
        try {
            // Flush INSERT before touching C's lifecycle: the partial unique index arbitrates races.
            assignments.saveAndFlush(assignment);
        } catch (DataIntegrityViolationException ex) {
            if (isActiveAssignmentConflict(ex)) throw duplicateAssignment();
            throw ex;
        }
        // C's participant synchronizes exactly one work order in this transaction.
        lifecycle.transition(request.ticketNumber(), RequestStatus.ASSIGNED, actorUserId, reason);
        events.publishEvent(new AssignmentCreatedEvent(assignment.getId(), request.id(), request.ticketNumber(),
                request.requesterId(), technicianId, previousTechnicianId, actorUserId, reason, assignment.getAssignedAt()));
        return AssignmentResponse.from(assignment);
    }

    private void deactivate(Assignment assignment, Long actorUserId, String reason) {
        assignment.deactivate(actorUserId, reason, now());
        // Flush the old row first to free the unique slot. @Version rejects concurrent edits.
        assignments.saveAndFlush(assignment);
    }

    private Assignment requireCurrent(Long requestId, Long expectedId) {
        return assignments.findByRequestIdAndActiveTrue(requestId)
                .filter(a -> a.getId().equals(expectedId)).orElseThrow(AssignmentService::staleAssignment);
    }

    private void requireEligible(RequestSnapshotResponse request, Long technicianId) {
        if (directory.findCandidates(request.category(), request.locationId()).stream()
                .noneMatch(candidate -> candidate.userId().equals(technicianId))) {
            throw new BusinessConflictException("The selected technician is no longer eligible for this request.");
        }
    }

    private RequestSnapshotResponse request(String ticketNumber) {
        if (ticketNumber == null || ticketNumber.isBlank()) {
            throw new InputValidationException("A ticket number is required.");
        }
        return requests.findByTicketNumber(ticketNumber);
    }

    private void requireAdministrator(Long actorUserId) {
        if (actorUserId == null) throw new AccessDeniedException("An active administrator is required.");
        var actor = users.getUserAccess(actorUserId);
        if (actor.role() != Role.ADMINISTRATOR || actor.accountStatus() != AccountStatus.ACTIVE) {
            throw new AccessDeniedException("An active administrator is required.");
        }
    }

    private <T> void validate(T command) {
        if (command == null || !validator.validate(command).isEmpty()) {
            throw new InputValidationException("Provide a valid technician, current assignment and reason (1–500 characters) as applicable.");
        }
    }

    private static boolean isActiveAssignmentConflict(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null
                    && violation.getConstraintName().toLowerCase(java.util.Locale.ROOT)
                    .contains("uk_assignments_active_request")) return true;
        }
        return false;
    }

    private static BusinessConflictException duplicateAssignment() {
        return new BusinessConflictException("This request already has an active assignment.");
    }

    private Instant now() {
        // PostgreSQL timestamps store microseconds; keep responses/events identical after a database round trip.
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static BusinessConflictException staleAssignment() {
        return new BusinessConflictException("The request or assignment changed. Reload before trying again.");
    }
}
