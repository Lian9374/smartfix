package com.smartfix.request.spi;

import java.util.Optional;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * B supplies an adapter to its public assignment read service, without injecting its write
 * orchestrator.
 */
public interface ActiveAssignmentLookup {
    record ActiveAssignment(Long assignmentId, Long requestId, Long technicianId) {}

    Optional<ActiveAssignment> findActiveAssignment(Long requestId);

    /** Existing adapters remain compatible; B can override this with a bulk read. */
    default Set<Long> findActiveRequestIds(Long technicianId, Collection<Long> candidateRequestIds) {
        return candidateRequestIds.stream()
                .filter(id -> findActiveAssignment(id)
                        .filter(a -> id.equals(a.requestId()) && technicianId.equals(a.technicianId()))
                        .isPresent())
                .collect(Collectors.toSet());
    }
}
