package com.smartfix.dispatch.repository;

import com.smartfix.dispatch.domain.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Set;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    Optional<Assignment> findByRequestIdAndActiveTrue(Long requestId);

    @Query("select a.requestId from Assignment a where a.active = true and a.technicianId = :technicianId")
    Set<Long> findActiveRequestIdsForTechnician(@Param("technicianId") Long technicianId);
}
