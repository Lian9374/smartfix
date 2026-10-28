package com.smartfix.dispatch.service;

import com.smartfix.dispatch.dto.DispatchPageResponse;
import com.smartfix.request.domain.RequestStatus;
import com.smartfix.request.service.RequestQueryService;
import com.smartfix.request.service.RequestReadService;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.service.UserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class DispatchPageService {
    private final RequestQueryService queries;
    private final RequestReadService requests;
    private final AssignmentReadService assignments;
    private final com.smartfix.technician.service.TechnicianDirectoryService directory;
    private final com.smartfix.technician.service.TechnicianWorkloadService workload;
    private final UserService users;

    public DispatchPageService(RequestQueryService queries, RequestReadService requests,
            AssignmentReadService assignments,
            com.smartfix.technician.service.TechnicianDirectoryService directory,
            com.smartfix.technician.service.TechnicianWorkloadService workload, UserService users) {
        this.queries = queries;
        this.requests = requests;
        this.assignments = assignments;
        this.directory = directory;
        this.workload = workload;
        this.users = users;
    }

    public DispatchPageResponse describe(String ticket, Long actorId) {
        if (actorId == null) throw new AccessDeniedException("An active administrator is required.");
        var actor = users.getUserAccess(actorId);
        if (actor.role() != Role.ADMINISTRATOR || actor.accountStatus() != AccountStatus.ACTIVE
                || actor.passwordChangeRequired()) {
            throw new AccessDeniedException("An active administrator is required.");
        }
        var detail = queries.getRequestDetails(ticket, actorId);
        var request = requests.findByTicketNumber(ticket);
        var current = assignments.findActiveAssignment(request.id()).orElse(null);
        String currentName = current == null ? null : users.listUsers().stream()
                .filter(user -> user.id().equals(current.technicianId()))
                .map(user -> user.displayName()).findFirst().orElse("Unavailable account");
        boolean canAssign = current == null && (request.status() == RequestStatus.SUBMITTED
                || request.status() == RequestStatus.UNDER_REVIEW);
        boolean canReassign = current != null && List.of(RequestStatus.ASSIGNED, RequestStatus.IN_PROGRESS,
                RequestStatus.REOPENED).contains(request.status());
        boolean canWithdraw = current != null && request.status() == RequestStatus.ASSIGNED;
        List<DispatchPageResponse.Candidate> candidates = List.of();
        String notice = null;
        if (canAssign || canReassign) {
            candidates = directory.listManualAssignees().stream()
                    .map(engineer -> new DispatchPageResponse.Candidate(engineer.id(), engineer.displayName(),
                            engineer.username(), workload.countOpenWorkOrders(engineer.id()),
                            current == null || !current.technicianId().equals(engineer.id())
                                    || request.status() == RequestStatus.REOPENED))
                    .toList();
        } else {
            notice = "This request is no longer open for assignment changes.";
        }
        return new DispatchPageResponse(detail, request.effectiveUrgencyLevel(), current, currentName,
                candidates, canAssign, canReassign, canWithdraw, notice);
    }

}
