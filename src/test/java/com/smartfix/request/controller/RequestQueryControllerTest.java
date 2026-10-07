package com.smartfix.request.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.request.domain.RequestStatus;
import com.smartfix.request.dto.MaintenanceRequestDetailsResponse;
import com.smartfix.request.service.RequestPresentationService;
import com.smartfix.request.service.RequestQueryService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.time.Instant;
import java.util.List;

class RequestQueryControllerTest {

    private RequestQueryService requestQueryService;
    private RequestQueryController controller;
    private SmartFixUserDetails principal;

    @BeforeEach
    void setUp() {
        requestQueryService = mock(RequestQueryService.class);

        controller =
                new RequestQueryController(
                        requestQueryService, mock(RequestPresentationService.class));

        principal = mock(SmartFixUserDetails.class);

        when(principal.getUserId()).thenReturn(10L);
    }

    @Test
    void myRequestsReturnsMineTemplate() {
        when(requestQueryService.listMyRequestsPage(10L, null, 0, 20)).thenReturn(Page.empty());

        Model model = new ConcurrentModel();

        String view = controller.myRequests(principal, 0, 20, null, model);

        assertEquals("request/mine", view);

        verify(requestQueryService).listMyRequestsPage(10L, null, 0, 20);
    }

    @Test
    void requestDetailsReturnsDetailTemplate() {
        MaintenanceRequestDetailsResponse response =
                new MaintenanceRequestDetailsResponse(
                        "SF-2026-000001",
                        10L,
                        5L,
                        "COM1 Level 1",
                        "Broken light",
                        "Light does not work",
                        null,
                        null,
                        RequestStatus.SUBMITTED,
                        Instant.parse("2026-09-19T00:00:00Z"),
                        Instant.parse("2026-09-19T00:00:00Z"),
                        List.of());

        when(requestQueryService.getRequestDetails("SF-2026-000001", 10L)).thenReturn(response);

        Model model = new ConcurrentModel();

        String view = controller.requestDetails("SF-2026-000001", principal, model);

        assertEquals("request/detail", view);
        assertSame(response, model.getAttribute("request"));
    }

    @Test
    void adminLookupWithoutTicketShowsLookupPage() {
        Model model = new ConcurrentModel();

        String view = controller.adminLookup(null, principal, model);

        assertEquals("admin/requests", view);

        verifyNoInteractions(requestQueryService);
    }

    @Test
    void adminLookupWithTicketLoadsRequest() {
        MaintenanceRequestDetailsResponse response = mock(MaintenanceRequestDetailsResponse.class);

        when(requestQueryService.getRequestDetails("SF-2026-000001", 10L)).thenReturn(response);

        Model model = new ConcurrentModel();

        String view = controller.adminLookup("SF-2026-000001", principal, model);

        assertEquals("admin/requests", view);

        assertSame(response, model.getAttribute("request"));

        verify(requestQueryService).getRequestDetails("SF-2026-000001", 10L);
    }
}
