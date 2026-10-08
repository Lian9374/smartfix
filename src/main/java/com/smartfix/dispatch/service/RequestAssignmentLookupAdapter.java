package com.smartfix.dispatch.service;

import com.smartfix.request.spi.ActiveAssignmentLookup;
import org.springframework.stereotype.Component;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RequestAssignmentLookupAdapter implements ActiveAssignmentLookup {
    private final AssignmentReadService assignments;

    public RequestAssignmentLookupAdapter(AssignmentReadService assignments) { this.assignments = assignments; }

    @Override
    public Optional<ActiveAssignment> findActiveAssignment(Long requestId) {
        return assignments.findActiveAssignment(requestId)
                .map(a -> new ActiveAssignment(a.id(), a.requestId(), a.technicianId()));
    }

    @Override
    public Set<Long> findActiveRequestIds(Long technicianId, Collection<Long> candidateRequestIds) {
        if (candidateRequestIds.isEmpty()) return Set.of();
        Set<Long> activeIds = assignments.findActiveRequestIdsForTechnician(technicianId);
        return candidateRequestIds.stream().filter(activeIds::contains).collect(Collectors.toUnmodifiableSet());
    }
}
