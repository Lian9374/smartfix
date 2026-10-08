package com.smartfix.dispatch.service;

import com.smartfix.dispatch.dto.DispatchPageResponse;
import com.smartfix.facility.service.LocationService;
import com.smartfix.request.domain.MaintenanceCategory;
import com.smartfix.request.domain.RequestStatus;
import com.smartfix.request.service.RequestPresentationService;
import com.smartfix.request.service.RequestQueryService;
import com.smartfix.request.service.RequestReadService;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.service.UserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DispatchPageService {
    private final RequestQueryService queries;
    private final RequestReadService requests;
    private final RequestPresentationService presentation;
    private final AssignmentReadService assignments;
    private final TechnicianRecommendationService recommendations;
    private final LocationService locations;
    private final UserService users;

    public DispatchPageService(RequestQueryService queries, RequestReadService requests,
            RequestPresentationService presentation, AssignmentReadService assignments,
            TechnicianRecommendationService recommendations, LocationService locations, UserService users) {
        this.queries = queries;
        this.requests = requests;
        this.presentation = presentation;
        this.assignments = assignments;
        this.recommendations = recommendations;
        this.locations = locations;
        this.users = users;
    }

    public DispatchPageResponse describe(String ticket, Long actorId) {
        if (actorId == null) throw new AccessDeniedException("An active administrator is required.");
        var actor = users.getUserAccess(actorId);
        if (actor.role() != Role.ADMINISTRATOR || actor.accountStatus() != AccountStatus.ACTIVE) {
            throw new AccessDeniedException("An active administrator is required.");
        }
        var detail = queries.getRequestDetails(ticket, actorId);
        var request = requests.findByTicketNumber(ticket);
        var current = assignments.findActiveAssignment(request.id()).orElse(null);
        String currentName = current == null ? null : users.listUsers().stream()
                .filter(user -> user.id().equals(current.technicianId()))
                .map(user -> user.displayName()).findFirst().orElse("Unavailable account");
        boolean canAssign = current == null && request.status() == RequestStatus.UNDER_REVIEW
                && presentation.describe(ticket, actorId).availableActions().contains("dispatch");
        boolean canReassign = current != null && List.of(RequestStatus.ASSIGNED, RequestStatus.IN_PROGRESS,
                RequestStatus.REOPENED).contains(request.status());
        boolean canWithdraw = current != null && request.status() == RequestStatus.ASSIGNED;
        List<DispatchPageResponse.Candidate> candidates = List.of();
        String notice = null;
        if (canAssign || canReassign) {
            if (!locations.getLocation(request.locationId()).active()) {
                notice = "This location is inactive. Assignment is unavailable until the location is restored.";
            } else {
                // Cache names within this page only; never query another module's repository.
                Map<Long, String> areaNames = new HashMap<>();
                candidates = recommendations.recommend(request.category(), request.locationId()).stream()
                        .map(candidate -> new DispatchPageResponse.Candidate(candidate.userId(), candidate.displayName(),
                                candidate.skills().stream().sorted().map(DispatchPageService::skillLabel).collect(Collectors.joining(", ")),
                                candidate.serviceAreaIds().stream().sorted()
                                        .map(id -> areaNames.computeIfAbsent(id, key -> locations.getLocation(key).displayName()))
                                        .collect(Collectors.joining(", ")),
                                candidate.availabilityStatus().name(), candidate.openWorkOrders(),
                                current == null || !current.technicianId().equals(candidate.userId())
                                        || request.status() == RequestStatus.REOPENED))
                        .toList();
            }
        } else {
            notice = request.status() == RequestStatus.SUBMITTED || request.status() == RequestStatus.UNDER_REVIEW
                    ? "Complete the request review and set its priority before assigning a technician."
                    : "Assignment changes are unavailable in this request's current state.";
        }
        return new DispatchPageResponse(detail, request.effectiveUrgencyLevel(), current, currentName,
                candidates, canAssign, canReassign, canWithdraw, notice);
    }

    private static String skillLabel(MaintenanceCategory category) {
        return switch (category) {
            case ELECTRICAL -> "Electrical";
            case PLUMBING -> "Plumbing";
            case HVAC -> "HVAC";
            case BUILDING -> "Building";
            case OTHER -> "Other";
        };
    }
}
