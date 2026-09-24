package com.smartfix.request.repository;

import com.smartfix.request.domain.RequestStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Persistence for immutable maintenance-request status history entries.
 */
public interface RequestStatusHistoryRepository
        extends JpaRepository<RequestStatusHistory, Long> {

    /**
     * Finds the complete status history for one maintenance request,
     * ordered from the earliest change to the latest change.
     *
     * @param requestId the maintenance request id
     * @return the request's status history in chronological order
     */
    List<RequestStatusHistory> findAllByRequestIdOrderByChangedAtAsc(
            Long requestId
    );
}