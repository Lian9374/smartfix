package com.smartfix.request.service;

import com.smartfix.common.exception.*;
import com.smartfix.request.domain.*;
import com.smartfix.request.repository.MaintenanceRequestRepository;
import com.smartfix.user.domain.*;
import com.smartfix.user.service.UserService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class RequestReviewService {
    private final RequestAccessService access;
    private final UserService users;
    private final RequestLifecycleService lifecycle;
    private final MaintenanceRequestRepository requests;
    private final Clock clock;

    public RequestReviewService(
            RequestAccessService access,
            UserService users,
            RequestLifecycleService lifecycle,
            MaintenanceRequestRepository requests,
            Clock clock) {
        this.access = access;
        this.users = users;
        this.lifecycle = lifecycle;
        this.requests = requests;
        this.clock = clock;
    }

    @Transactional
    public void review(
            String ticket, UrgencyLevel priority, Long actorId, boolean reject, String reason) {
        var actor = users.getUserAccess(actorId);
        if (actor.role() != Role.ADMINISTRATOR || actor.accountStatus() != AccountStatus.ACTIVE) {
            throw new ResourceNotFoundException("Request not found");
        }
        if (priority == null) throw new InputValidationException("A final priority is required.");
        MaintenanceRequest request = access.requireReadableRequest(ticket, actorId);
        if (request.getStatus() == RequestStatus.SUBMITTED)
            lifecycle.transition(ticket, RequestStatus.UNDER_REVIEW, actorId, null);
        request.recordReview(actorId, priority, clock.instant());
        requests.saveAndFlush(request);
        if (reject) lifecycle.transition(ticket, RequestStatus.REJECTED, actorId, reason);
    }
}
