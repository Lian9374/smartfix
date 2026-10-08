package com.smartfix.workorder.repository;

import com.smartfix.workorder.domain.*;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {
    Optional<WorkOrder> findByRequestId(Long requestId);

    Page<WorkOrder> findByTechnicianIdAndRequestIdIn(
            Long technicianId, Collection<Long> requestIds, Pageable pageable);

    List<WorkOrder> findAllByTechnicianIdAndStatusIn(
            Long technicianId, Collection<WorkOrderStatus> statuses);
}
