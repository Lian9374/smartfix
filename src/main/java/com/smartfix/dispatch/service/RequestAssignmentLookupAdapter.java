package com.smartfix.dispatch.service;

import com.smartfix.request.spi.ActiveAssignmentLookup;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
public class RequestAssignmentLookupAdapter implements ActiveAssignmentLookup {
    private final AssignmentReadService assignments;

    public RequestAssignmentLookupAdapter(AssignmentReadService assignments) { this.assignments = assignments; }

    @Override
    public Optional<ActiveAssignment> findActiveAssignment(Long requestId) {
        return assignments.findActiveAssignment(requestId)
                .map(a -> new ActiveAssignment(a.id(), a.requestId(), a.technicianId()));
    }
}
