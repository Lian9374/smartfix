package com.smartfix.workorder.repository;

import com.smartfix.workorder.domain.RepairRecord;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RepairRecordRepository extends JpaRepository<RepairRecord, Long> {
    List<RepairRecord> findByWorkOrderIdOrderByCreatedAtAscIdAsc(Long workOrderId);

    boolean existsByWorkOrderId(Long workOrderId);
}
