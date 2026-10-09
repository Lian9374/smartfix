package com.smartfix.technician.repository;

import com.smartfix.request.domain.MaintenanceCategory;
import com.smartfix.technician.domain.AvailabilityStatus;
import com.smartfix.technician.domain.TechnicianProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TechnicianProfileRepository extends JpaRepository<TechnicianProfile, Long> {
    Optional<TechnicianProfile> findByUserId(Long userId);

    @Query("""
            select p from TechnicianProfile p
            where p.active = true and p.availabilityStatus <> :excludedAvailability
              and :category member of p.skills and :locationId member of p.serviceAreaIds
            order by p.id asc
            """)
    List<TechnicianProfile> findEligibleProfiles(@Param("category") MaintenanceCategory category,
            @Param("locationId") Long locationId,
            @Param("excludedAvailability") AvailabilityStatus excludedAvailability);
}
