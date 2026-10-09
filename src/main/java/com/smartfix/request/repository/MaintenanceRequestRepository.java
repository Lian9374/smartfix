package com.smartfix.request.repository;

import com.smartfix.request.domain.MaintenanceRequest;
import com.smartfix.request.domain.RequestStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.smartfix.request.dto.LocationRequestCount;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence owned by the request module for the maintenance-request aggregate root. */
public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, Long> {

    @Query("select new com.smartfix.request.dto.LocationRequestCount(r.locationId, r.status, count(r)) "
            + "from MaintenanceRequest r where r.status in :statuses group by r.locationId, r.status")
    List<LocationRequestCount> countByLocationAndStatus(@Param("statuses") List<RequestStatus> statuses);

    Page<MaintenanceRequest> findByRequesterId(Long requesterId, Pageable pageable);

    Page<MaintenanceRequest> findByRequesterIdAndStatus(
            Long requesterId, RequestStatus status, Pageable pageable);

    Page<MaintenanceRequest> findByStatus(RequestStatus status, Pageable pageable);

    long countByStatus(RequestStatus status);

    Page<MaintenanceRequest> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Instant start, Instant end, Pageable pageable);

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
            Long requesterId, Pageable pageable);
}
