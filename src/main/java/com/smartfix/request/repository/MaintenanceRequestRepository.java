package com.smartfix.request.repository;

import com.smartfix.request.domain.MaintenanceRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Persistence owned by the request module for the maintenance-request aggregate root.
 */
public interface MaintenanceRequestRepository
        extends JpaRepository<MaintenanceRequest, Long> {

    /**
     * Finds a maintenance request by its unique public ticket number.
     *
     * @param ticketNumber the request ticket number
     * @return the matching request, or empty when no request exists
     */
    Optional<MaintenanceRequest> findByTicketNumber(String ticketNumber);

    /**
     * Finds requests submitted by one requester, ordered from newest to oldest.
     *
     * @param requesterId the requester's user id
     * @param pageable pagination information
     * @return requests submitted by the requester
     */
    List<MaintenanceRequest> findAllByRequesterIdOrderByCreatedAtDesc(
            Long requesterId,
            Pageable pageable
    );
}