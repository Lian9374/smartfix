package com.smartfix.request.service;

import com.smartfix.common.exception.*;
import com.smartfix.request.domain.*;
import com.smartfix.request.repository.RequestFeedbackRepository;
import com.smartfix.user.domain.Role;
import com.smartfix.user.service.UserService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class RequestConfirmationService {
    private final RequestAccessService access;
    private final RequestLifecycleService lifecycle;
    private final RequestFeedbackRepository feedback;
    private final UserService users;
    private final Clock clock;

    public RequestConfirmationService(
            RequestAccessService access,
            RequestLifecycleService lifecycle,
            RequestFeedbackRepository feedback,
            UserService users,
            Clock clock) {
        this.access = access;
        this.lifecycle = lifecycle;
        this.feedback = feedback;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public void confirm(String ticket, Long actorId) {
        lifecycle.transition(ticket, RequestStatus.CONFIRMED, actorId, null);
    }

    @Transactional
    public void reopen(String ticket, Long actorId, String reason) {
        lifecycle.transition(ticket, RequestStatus.REOPENED, actorId, reason);
    }

    @Transactional
    public void close(String ticket, Long actorId) {
        lifecycle.transition(ticket, RequestStatus.CLOSED, actorId, null);
    }

    @Transactional
    public void cancel(String ticket, Long actorId) {
        lifecycle.transition(ticket, RequestStatus.CANCELLED, actorId, null);
    }

    @Transactional
    public void feedback(String ticket, Long actorId, int rating, String comment) {
        MaintenanceRequest request = access.requireReadableRequest(ticket, actorId);
        if (!request.getRequesterId().equals(actorId)
                || users.getUserAccess(actorId).role() != Role.REQUESTER) {
            throw new ResourceNotFoundException("Request not found");
        }
        if (request.getStatus() != RequestStatus.CONFIRMED
                && request.getStatus() != RequestStatus.CLOSED) {
            throw new BusinessConflictException("Confirm the solution before leaving feedback.");
        }
        if (feedback.existsByRequestId(request.getId()))
            throw new BusinessConflictException("Feedback is already recorded.");
        feedback.saveAndFlush(
                RequestFeedback.create(request.getId(), actorId, rating, comment, clock.instant()));
    }
}
