package com.smartfix.workorder.repository;

import com.smartfix.workorder.domain.*;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {
    Optional<WorkOrder> findByRequestId(Long requestId);

    Page<WorkOrder> findByTechnicianId(Long technicianId, Pageable pageable);

    @Query("select w.requestId from WorkOrder w where w.technicianId = :technicianId")
    List<Long> findRequestIdsByTechnicianId(@Param("technicianId") Long technicianId);

    Page<WorkOrder> findByTechnicianIdAndRequestIdIn(
            Long technicianId, Collection<Long> requestIds, Pageable pageable);

    List<WorkOrder> findAllByTechnicianIdAndStatusIn(
            Long technicianId, Collection<WorkOrderStatus> statuses);
}
