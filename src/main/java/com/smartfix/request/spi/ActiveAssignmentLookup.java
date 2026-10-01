package com.smartfix.request.spi;

import java.util.Optional;

/**
 * B supplies an adapter to its public assignment read service, without injecting its write
 * orchestrator.
 */
public interface ActiveAssignmentLookup {
    record ActiveAssignment(Long assignmentId, Long requestId, Long technicianId) {}

    Optional<ActiveAssignment> findActiveAssignment(Long requestId);
}
