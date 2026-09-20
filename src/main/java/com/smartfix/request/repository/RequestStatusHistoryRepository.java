package com.smartfix.request.repository;

import com.smartfix.request.domain.RequestStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for immutable maintenance-request status history entries. */
public interface RequestStatusHistoryRepository extends JpaRepository<RequestStatusHistory, Long> {
}
