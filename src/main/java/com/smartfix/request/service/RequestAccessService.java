package com.smartfix.request.service;

import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.request.domain.MaintenanceRequest;
import com.smartfix.request.repository.MaintenanceRequestRepository;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.UserAccessResponse;
import com.smartfix.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RequestAccessService {

    private final MaintenanceRequestRepository maintenanceRequestRepository;
    private final UserService userService;

    public RequestAccessService(
        MaintenanceRequestRepository maintenanceRequestRepository,
        UserService userService
    ) {
        this.maintenanceRequestRepository = maintenanceRequestRepository;
        this.userService = userService;
    }

    public MaintenanceRequest requireReadableRequest(
        String ticketNumber,
        Long actorUserId
    ) {
        MaintenanceRequest request =
            maintenanceRequestRepository.findByTicketNumber(ticketNumber)
                .orElseThrow(this::notFound);

        UserAccessResponse actor = userService.getUserAccess(actorUserId);

        if (actor.accountStatus() != AccountStatus.ACTIVE) {
            throw notFound();
        }

        if (actor.role() == Role.ADMINISTRATOR) {
            return request;
        }

        if (actor.role() == Role.REQUESTER
            && request.getRequesterId().equals(actorUserId)) {
            return request;
        }

        throw notFound();
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Request not found");
    }
}
