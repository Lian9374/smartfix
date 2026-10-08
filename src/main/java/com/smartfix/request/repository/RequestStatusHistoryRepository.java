package com.smartfix.request.repository;

import com.smartfix.request.domain.RequestStatusHistory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Persistence for immutable maintenance-request status history entries. */
public interface RequestStatusHistoryRepository extends JpaRepository<RequestStatusHistory, Long> {

    boolean existsByRequestId(Long requestId);

    /**
     * Finds the complete status history for one maintenance request, ordered from the earliest
     * change to the latest change.
     *
     * @param requestId the maintenance request id
     * @return the request's status history in chronological order
     */
    @Query(
            "select h from RequestStatusHistory h where h.requestId = :requestId order by"
                    + " h.changedAt asc, h.id asc")
    List<RequestStatusHistory> findAllByRequestIdOrderByChangedAtAsc(
            @Param("requestId") Long requestId);
}
