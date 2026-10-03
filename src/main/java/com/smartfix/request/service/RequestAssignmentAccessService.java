package com.smartfix.request.service;

import com.smartfix.common.exception.BusinessConflictException;
import com.smartfix.request.spi.ActiveAssignmentLookup;
import com.smartfix.request.spi.ActiveAssignmentLookup.ActiveAssignment;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.service.UserService;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class RequestAssignmentAccessService {
    private final ObjectProvider<ActiveAssignmentLookup> lookup;
    private final UserService users;

    public RequestAssignmentAccessService(
            ObjectProvider<ActiveAssignmentLookup> lookup, UserService users) {
        this.lookup = lookup;
        this.users = users;
    }

    public Optional<ActiveAssignment> findActiveAssignment(Long requestId) {
        ActiveAssignmentLookup adapter = lookup.getIfAvailable();
        if (adapter == null) return Optional.empty();
        return adapter.findActiveAssignment(requestId).filter(a -> requestId.equals(a.requestId()));
    }

    public boolean isAssignedTo(Long requestId, Long technicianId) {
        return findActiveAssignment(requestId)
                .map(a -> technicianId.equals(a.technicianId()))
                .orElse(false);
    }

    public ActiveAssignment requireActiveAssignment(Long requestId) {
        ActiveAssignment a =
                findActiveAssignment(requestId)
                        .orElseThrow(
                                () ->
                                        new BusinessConflictException(
                                                "No active assignment is available. Complete"
                                                        + " dispatch first."));
        var technician = users.getUserAccess(a.technicianId());
        if (technician.accountStatus() != AccountStatus.ACTIVE
                || technician.role() != Role.TECHNICIAN) {
            throw new BusinessConflictException("The assigned technician is unavailable.");
        }
        return a;
    }

    public boolean isAvailable() {
        return lookup.getIfAvailable() != null;
    }
}
