package com.smartfix.request.spi;

import java.util.Optional;
import java.util.Set;

/**
 * B supplies an adapter to its public assignment read service, without injecting its write
 * orchestrator.
 */
public interface ActiveAssignmentLookup {
    record ActiveAssignment(Long assignmentId, Long requestId, Long technicianId) {}

    Optional<ActiveAssignment> findActiveAssignment(Long requestId);

    /**
     * Request ids currently assigned to this account, for authorization before pagination.
     * Older/missing adapters must not fall back to historical work-order ownership.
     */
    default Set<Long> findActiveRequestIdsForTechnician(Long technicianId) {
        return Set.of();
    }
}
