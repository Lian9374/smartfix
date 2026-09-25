package com.smartfix.request.service;

import com.smartfix.facility.dto.LocationResponse;
import com.smartfix.facility.service.LocationService;
import com.smartfix.request.domain.MaintenanceRequest;
import com.smartfix.request.domain.RequestStatusHistory;
import com.smartfix.request.dto.MaintenanceRequestDetailsResponse;
import com.smartfix.request.dto.MaintenanceRequestSummaryResponse;
import com.smartfix.request.dto.RequestStatusHistoryResponse;
import com.smartfix.request.repository.MaintenanceRequestRepository;
import com.smartfix.request.repository.RequestStatusHistoryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
        LocationService locationService
    ) {
        this.maintenanceRequestRepository = maintenanceRequestRepository;
        this.requestStatusHistoryRepository = requestStatusHistoryRepository;
        this.requestAccessService = requestAccessService;
        this.locationService = locationService;
    }

    public List<MaintenanceRequestSummaryResponse> listMyRequests(
        Long actorUserId,
        int page,
        int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0
            ? DEFAULT_PAGE_SIZE
            : Math.min(size, MAX_PAGE_SIZE);

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
        String ticketNumber,
        Long actorUserId
    ) {
        MaintenanceRequest request =
            requestAccessService.requireReadableRequest(
                ticketNumber,
                actorUserId
            );

        LocationResponse location =
            locationService.getLocation(request.getLocationId());

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
            history
        );
    }

    private MaintenanceRequestSummaryResponse toSummaryResponse(
        MaintenanceRequest request
    ) {
        LocationResponse location =
            locationService.getLocation(request.getLocationId());

        return new MaintenanceRequestSummaryResponse(
            request.getTicketNumber(),
            request.getTitle(),
            request.getCategory(),
            request.getUrgencyLevel(),
            request.getStatus(),
            location.displayName(),
            request.getCreatedAt()
        );
    }

    private RequestStatusHistoryResponse toHistoryResponse(
        RequestStatusHistory history
    ) {
        return new RequestStatusHistoryResponse(
            history.getFromStatus(),
            history.getToStatus(),
            history.getChangedByUserId(),
            history.getChangedAt(),
            history.getComment()
        );
    }
}
