package com.smartfix.request.service;

import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.request.domain.MaintenanceRequest;
import com.smartfix.request.repository.MaintenanceRequestRepository;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.UserAccessResponse;
import com.smartfix.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequestAccessServiceTest {

    private MaintenanceRequestRepository requestRepository;
    private UserService userService;
    private RequestAccessService requestAccessService;

    @BeforeEach
    void setUp() {
        requestRepository =
            mock(MaintenanceRequestRepository.class);

        userService =
            mock(UserService.class);

        requestAccessService =
            new RequestAccessService(
                requestRepository,
                userService
            );
    }

    @Test
    void requesterCanReadOwnRequest() {
        MaintenanceRequest request =
            mock(MaintenanceRequest.class);

        when(request.getRequesterId())
            .thenReturn(10L);

        when(requestRepository
            .findByTicketNumber("SF-2026-000001"))
            .thenReturn(Optional.of(request));

        when(userService.getUserAccess(10L))
            .thenReturn(
                new UserAccessResponse(
                    10L,
                    Role.REQUESTER,
                    AccountStatus.ACTIVE,
                    0L
                )
            );

        MaintenanceRequest result =
            requestAccessService.requireReadableRequest(
                "SF-2026-000001",
                10L
            );

        assertSame(request, result);
    }

    @Test
    void requesterCannotReadAnotherUsersRequest() {
        MaintenanceRequest request =
            mock(MaintenanceRequest.class);

        when(request.getRequesterId())
            .thenReturn(10L);

        when(requestRepository
            .findByTicketNumber("SF-2026-000001"))
            .thenReturn(Optional.of(request));

        when(userService.getUserAccess(20L))
            .thenReturn(
                new UserAccessResponse(
                    20L,
                    Role.REQUESTER,
                    AccountStatus.ACTIVE,
                    0L
                )
            );

        assertThrows(
            ResourceNotFoundException.class,
            () ->
                requestAccessService.requireReadableRequest(
                    "SF-2026-000001",
                    20L
                )
        );
    }

    @Test
    void administratorCanReadRequest() {
        MaintenanceRequest request =
            mock(MaintenanceRequest.class);

        when(requestRepository
            .findByTicketNumber("SF-2026-000001"))
            .thenReturn(Optional.of(request));

        when(userService.getUserAccess(99L))
            .thenReturn(
                new UserAccessResponse(
                    99L,
                    Role.ADMINISTRATOR,
                    AccountStatus.ACTIVE,
                    0L
                )
            );

        assertSame(
            request,
            requestAccessService.requireReadableRequest(
                "SF-2026-000001",
                99L
            )
        );
    }

    @Test
    void technicianCannotReadRequest() {
        MaintenanceRequest request =
            mock(MaintenanceRequest.class);

        when(requestRepository
            .findByTicketNumber("SF-2026-000001"))
            .thenReturn(Optional.of(request));

        when(userService.getUserAccess(30L))
            .thenReturn(
                new UserAccessResponse(
                    30L,
                    Role.TECHNICIAN,
                    AccountStatus.ACTIVE,
                    0L
                )
            );

        assertThrows(
            ResourceNotFoundException.class,
            () ->
                requestAccessService.requireReadableRequest(
                    "SF-2026-000001",
                    30L
                )
        );
    }

    @Test
    void missingRequestReturnsNotFound() {
        when(requestRepository
            .findByTicketNumber("SF-2026-999999"))
            .thenReturn(Optional.empty());

        assertThrows(
            ResourceNotFoundException.class,
            () ->
                requestAccessService.requireReadableRequest(
                    "SF-2026-999999",
                    10L
                )
        );

        verifyNoInteractions(userService);
    }
}
