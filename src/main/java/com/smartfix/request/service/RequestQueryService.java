package com.smartfix.request.service;

import com.smartfix.facility.dto.LocationResponse;
import com.smartfix.facility.service.LocationService;
import com.smartfix.request.domain.MaintenanceRequest;
import com.smartfix.request.domain.RequestStatus;
import com.smartfix.request.domain.RequestStatusHistory;
import com.smartfix.request.dto.MaintenanceRequestDetailsResponse;
import com.smartfix.request.dto.MaintenanceRequestSummaryResponse;
import com.smartfix.request.dto.RequestStatusHistoryResponse;
import com.smartfix.request.repository.MaintenanceRequestRepository;
import com.smartfix.request.repository.RequestStatusHistoryRepository;
import com.smartfix.user.domain.Role;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class RequestQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final MaintenanceRequestRepository maintenanceRequestRepository;
    private final RequestStatusHistoryRepository requestStatusHistoryRepository;
    private final RequestAccessService requestAccessService;
    private final LocationService locationService;

    public RequestQueryService(
            MaintenanceRequestRepository maintenanceRequestRepository,
            RequestStatusHistoryRepository requestStatusHistoryRepository,
            RequestAccessService requestAccessService,
            LocationService locationService) {
        this.maintenanceRequestRepository = maintenanceRequestRepository;
        this.requestStatusHistoryRepository = requestStatusHistoryRepository;
        this.requestAccessService = requestAccessService;
        this.locationService = locationService;
    }

    public List<MaintenanceRequestSummaryResponse> listMyRequests(
            Long actorUserId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(safePage, safeSize);

        /*
         * IMPORTANT:
         * Filter by requesterId in the database.
         * Never findAll() and filter in Java.
         */
        return maintenanceRequestRepository
                .findAllByRequesterIdOrderByCreatedAtDesc(actorUserId, pageable)
                .stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    public MaintenanceRequestDetailsResponse getRequestDetails(
            String ticketNumber, Long actorUserId) {
        MaintenanceRequest request =
                requestAccessService.requireReadableRequest(ticketNumber, actorUserId);

        LocationResponse location = locationService.getLocation(request.getLocationId());

        List<RequestStatusHistoryResponse> history =
                requestStatusHistoryRepository
                        .findAllByRequestIdOrderByChangedAtAsc(request.getId())
                        .stream()
                        .map(this::toHistoryResponse)
                        .toList();

        return new MaintenanceRequestDetailsResponse(
                request.getTicketNumber(),
                request.getRequesterId(),
                request.getLocationId(),
                location.displayName(),
                request.getTitle(),
                request.getDescription(),
                request.getCategory(),
                request.getUrgencyLevel(),
                request.getStatus(),
                request.getCreatedAt(),
                request.getUpdatedAt(),
                history);
    }

    public Page<MaintenanceRequestSummaryResponse> listMyRequestsPage(
            Long actorId, RequestStatus status, int page, int size) {
        requestAccessService.requireRole(actorId, Role.REQUESTER);
        Pageable pageable = pageRequest(page, size);
        Page<MaintenanceRequest> result =
                status == null
                        ? maintenanceRequestRepository.findByRequesterId(actorId, pageable)
                        : maintenanceRequestRepository.findByRequesterIdAndStatus(
                                actorId, status, pageable);
        return result.map(this::toSummaryResponse);
    }

    public Page<MaintenanceRequestSummaryResponse> listForReview(
            Long actorId, RequestStatus status, int page, int size) {
        requestAccessService.requireRole(actorId, Role.ADMINISTRATOR);
        Page<MaintenanceRequest> result =
                status == null
                        ? maintenanceRequestRepository.findAll(pageRequest(page, size))
                        : maintenanceRequestRepository.findByStatus(
                                status, pageRequest(page, size));
        return result.map(this::toSummaryResponse);
    }

    private Pageable pageRequest(int page, int size) {
        return PageRequest.of(
                Math.max(page, 0),
                size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    public java.util.Map<RequestStatus, Long> adminStatusCounts(Long actorId) {
        requestAccessService.requireRole(actorId, Role.ADMINISTRATOR);
        var counts = new java.util.EnumMap<RequestStatus, Long>(RequestStatus.class);
        for (var status : RequestStatus.values()) counts.put(status, 0L);
        maintenanceRequestRepository.countStatuses().forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        return java.util.Map.copyOf(counts);
    }

    public Page<com.smartfix.request.dto.AdminRequestRow> searchForAdministration(
            Long actorId, String search, RequestStatus status,
            com.smartfix.request.domain.MaintenanceCategory category,
            com.smartfix.request.domain.UrgencyLevel priority, String order, int page, int size) {
        requestAccessService.requireRole(actorId, Role.ADMINISTRATOR);
        String term = search == null ? "" : search.trim().toLowerCase(java.util.Locale.ROOT);
        if (term.length() > 120) throw new com.smartfix.common.exception.InputValidationException("Search is limited to 120 characters.");
        String sortField = switch (order == null ? "" : order) {
            case "updated" -> "updatedAt";
            case "oldest" -> "createdAt";
            default -> "createdAt";
        };
        var direction = "oldest".equals(order) ? Sort.Direction.ASC : Sort.Direction.DESC;
        var pageable = PageRequest.of(Math.max(0, page), size <= 0 ? 20 : Math.min(size, 100),
                Sort.by(direction, sortField, "id"));
        org.springframework.data.jpa.domain.Specification<MaintenanceRequest> filters = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (!term.isEmpty()) {
                String escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                String pattern = "%" + escaped + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("ticketNumber")), pattern, '\\'),
                        cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\')));
            }
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (category != null) predicates.add(cb.equal(root.get("category"), category));
            if (priority != null) predicates.add(cb.equal(cb.coalesce(root.get("finalUrgencyLevel"), root.get("urgencyLevel")), priority));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return maintenanceRequestRepository.findAll(filters, pageable).map(r ->
                new com.smartfix.request.dto.AdminRequestRow(r.getId(), r.getTicketNumber(), r.getTitle(),
                        r.getCategory(), r.getEffectiveUrgencyLevel(), r.getStatus(),
                        locationService.getLocation(r.getLocationId()).displayName(), r.getCreatedAt()));
    }

    private MaintenanceRequestSummaryResponse toSummaryResponse(MaintenanceRequest request) {
        LocationResponse location = locationService.getLocation(request.getLocationId());

        return new MaintenanceRequestSummaryResponse(
                request.getTicketNumber(),
                request.getTitle(),
                request.getCategory(),
                request.getUrgencyLevel(),
                request.getStatus(),
                location.displayName(),
                request.getCreatedAt());
    }

    private RequestStatusHistoryResponse toHistoryResponse(RequestStatusHistory history) {
        return new RequestStatusHistoryResponse(
                history.getFromStatus(),
                history.getToStatus(),
                history.getChangedByUserId(),
                history.getChangedAt(),
                history.getComment());
    }
}
