package com.smartfix.request.repository;

import com.smartfix.request.domain.MaintenanceRequest;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence owned by the request module for the maintenance-request aggregate root. */
public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, Long> {
}
