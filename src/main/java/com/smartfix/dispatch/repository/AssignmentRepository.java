package com.smartfix.dispatch.repository;

import com.smartfix.dispatch.domain.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    Optional<Assignment> findByRequestIdAndActiveTrue(Long requestId);
}
