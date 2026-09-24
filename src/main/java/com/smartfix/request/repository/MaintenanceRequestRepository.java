package com.smartfix.request.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartfix.request.domain.MaintenanceRequest;

/**
 * Persistence owned by the request module for the maintenance-request aggregate root.
 */
public interface MaintenanceRequestRepository
        extends JpaRepository<MaintenanceRequest, Long> {

    Optional<MaintenanceRequest> findByTicketNumber(
            String ticketNumber
    );
}