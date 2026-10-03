package com.smartfix.request.service;

import com.smartfix.request.domain.*;
import com.smartfix.request.dto.RequestPresentationResponse;
import com.smartfix.request.repository.RequestFeedbackRepository;
import com.smartfix.request.spi.RequestDetailContributor;
import com.smartfix.user.domain.Role;
import com.smartfix.user.service.UserService;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class RequestPresentationService {
    private final RequestAccessService access;
    private final AttachmentService attachments;
    private final RequestFeedbackRepository feedback;
    private final RequestAssignmentAccessService assignments;
    private final UserService users;
    private final ObjectProvider<RequestDetailContributor> contributors;

    public RequestPresentationService(
            RequestAccessService access,
            AttachmentService attachments,
            RequestFeedbackRepository feedback,
            RequestAssignmentAccessService assignments,
            UserService users,
            ObjectProvider<RequestDetailContributor> contributors) {
        this.access = access;
        this.attachments = attachments;
        this.feedback = feedback;
        this.assignments = assignments;
        this.users = users;
        this.contributors = contributors;
    }

    public RequestPresentationResponse describe(String ticket, Long actorId) {
        var r = access.requireReadableRequest(ticket, actorId);
        var role = users.getUserAccess(actorId).role();
        Set<String> actions = new HashSet<>();
        if (role == Role.ADMINISTRATOR) {
            if (r.getStatus() == RequestStatus.SUBMITTED
                    || r.getStatus() == RequestStatus.UNDER_REVIEW) actions.add("review");
            if (r.getStatus() == RequestStatus.CONFIRMED) actions.add("close");
            if (assignments.isAvailable()
                    && r.getFinalUrgencyLevel() != null
                    && (r.getStatus() == RequestStatus.UNDER_REVIEW
                            || r.getStatus() == RequestStatus.REOPENED)) actions.add("dispatch");
        }
        if (role == Role.REQUESTER && r.getRequesterId().equals(actorId)) {
            if (r.getStatus() == RequestStatus.SUBMITTED
                    || r.getStatus() == RequestStatus.UNDER_REVIEW) actions.add("cancel");
            if (r.getStatus() == RequestStatus.RESOLVED) actions.add("confirm");
            if (r.getStatus() == RequestStatus.RESOLVED || r.getStatus() == RequestStatus.CONFIRMED)
                actions.add("reopen");
            if ((r.getStatus() == RequestStatus.CONFIRMED || r.getStatus() == RequestStatus.CLOSED)
                    && !feedback.existsByRequestId(r.getId())) actions.add("feedback");
        }
        List<RequestDetailContributor.DetailItem> items = new ArrayList<>();
        items.add(
                new RequestDetailContributor.DetailItem(
                        "Reviewed priority",
                        r.getFinalUrgencyLevel() == null
                                ? "Pending review"
                                : r.getFinalUrgencyLevel().name()));
        assignments
                .findActiveAssignment(r.getId())
                .ifPresent(
                        a ->
                                items.add(
                                        new RequestDetailContributor.DetailItem(
                                                "Assigned technician",
                                                a.technicianId().toString())));
        contributors.orderedStream().forEach(c -> items.addAll(c.describe(r.getId())));
        var f = feedback.findByRequestId(r.getId());
        return new RequestPresentationResponse(
                attachments.findByRequestId(r.getId()),
                List.copyOf(items),
                Set.copyOf(actions),
                f.map(RequestFeedback::getRating).orElse(null),
                f.map(RequestFeedback::getComment).orElse(null));
    }
}
