package com.smartfix.dispatch.service;

import com.smartfix.dispatch.dto.AssignmentResponse;
import com.smartfix.dispatch.repository.AssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

/** Separate from the write orchestrator so C can read assignments without a service cycle. */
@Service
@Transactional(readOnly = true)
public class AssignmentReadService {
    private final AssignmentRepository assignments;

    public AssignmentReadService(AssignmentRepository assignments) { this.assignments = assignments; }

    public Optional<AssignmentResponse> findActiveAssignment(Long requestId) {
        return assignments.findByRequestIdAndActiveTrue(requestId).map(AssignmentResponse::from);
    }
}
