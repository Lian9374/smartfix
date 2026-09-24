package com.smartfix.request.service;

import com.smartfix.facility.dto.LocationResponse;
import com.smartfix.facility.service.LocationService;
import com.smartfix.request.domain.MaintenanceRequest;
import com.smartfix.request.domain.RequestStatus;
import com.smartfix.request.dto.MaintenanceRequestDetailsResponse;
import com.smartfix.request.dto.MaintenanceRequestSummaryResponse;
import com.smartfix.request.repository.MaintenanceRequestRepository;
import com.smartfix.request.repository.RequestStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RequestQueryServiceTest {

    private MaintenanceRequestRepository requestRepository;
    private RequestStatusHistoryRepository historyRepository;
    private RequestAccessService accessService;
    private LocationService locationService;

    private RequestQueryService requestQueryService;

    @BeforeEach
    void setUp() {
        requestRepository =
            mock(MaintenanceRequestRepository.class);

        historyRepository =
            mock(RequestStatusHistoryRepository.class);

        accessService =
            mock(RequestAccessService.class);

        locationService =
            mock(LocationService.class);

        requestQueryService =
            new RequestQueryService(
                requestRepository,
                historyRepository,
                accessService,
                locationService
            );
    }

    @Test
    void listMyRequestsQueriesByRequesterId() {
        MaintenanceRequest request =
            mock(MaintenanceRequest.class);

        when(request.getTicketNumber())
            .thenReturn("SF-2026-000001");

        when(request.getTitle())
            .thenReturn("Broken light");

        when(request.getLocationId())
            .thenReturn(5L);

        when(request.getStatus())
            .thenReturn(RequestStatus.SUBMITTED);

        when(request.getCreatedAt())
            .thenReturn(Instant.parse(
                "2026-09-19T00:00:00Z"
            ));

        when(requestRepository
            .findAllByRequesterIdOrderByCreatedAtDesc(
                eq(10L),
                any(Pageable.class)
            ))
            .thenReturn(List.of(request));

        when(locationService.getLocation(5L))
            .thenReturn(
                new LocationResponse(
                    5L,
                    "COM1-01",
                    "COM1",
                    "01",
                    null,
                    "COM1 Level 1",
                    true
                )
            );

        List<MaintenanceRequestSummaryResponse> result =
            requestQueryService.listMyRequests(
                10L,
                0,
                20
            );

        assertEquals(1, result.size());

        assertEquals(
            "SF-2026-000001",
            result.getFirst().ticketNumber()
        );

        verify(requestRepository)
            .findAllByRequesterIdOrderByCreatedAtDesc(
                eq(10L),
                any(Pageable.class)
            );

        verify(requestRepository, never()).findAll();
    }

    @Test
    void getRequestDetailsUsesCentralAccessService() {
        MaintenanceRequest request =
            mock(MaintenanceRequest.class);

        when(request.getId()).thenReturn(100L);
        when(request.getTicketNumber())
            .thenReturn("SF-2026-000001");
        when(request.getRequesterId()).thenReturn(10L);
        when(request.getLocationId()).thenReturn(5L);
        when(request.getTitle()).thenReturn("Broken light");
        when(request.getDescription())
            .thenReturn("Light is not working");
        when(request.getStatus())
            .thenReturn(RequestStatus.SUBMITTED);

        Instant created =
            Instant.parse("2026-09-19T00:00:00Z");

        when(request.getCreatedAt()).thenReturn(created);
        when(request.getUpdatedAt()).thenReturn(created);

        when(accessService.requireReadableRequest(
            "SF-2026-000001",
            10L
        )).thenReturn(request);

        when(locationService.getLocation(5L))
            .thenReturn(
                new LocationResponse(
                    5L,
                    "COM1-01",
                    "COM1",
                    "01",
                    null,
                    "COM1 Level 1",
                    true
                )
            );

        when(historyRepository
            .findAllByRequestIdOrderByChangedAtAsc(100L))
            .thenReturn(List.of());

        MaintenanceRequestDetailsResponse result =
            requestQueryService.getRequestDetails(
                "SF-2026-000001",
                10L
            );

        assertEquals(
            "SF-2026-000001",
            result.ticketNumber()
        );

        verify(accessService)
            .requireReadableRequest(
                "SF-2026-000001",
                10L
            );
    }
}
