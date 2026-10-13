package com.smartfix.facility.repository;

import com.smartfix.facility.domain.Facility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;

public interface FacilityRepository extends JpaRepository<Facility, Long> {

    @EntityGraph(attributePaths = "location")
    List<Facility> findAllByOrderByNameAsc();

    List<Facility> findAllByLocationIdOrderByNameAsc(Long locationId);

    @EntityGraph(attributePaths = "location")
    List<Facility> findByLocationActiveTrueOrderByNameAscIdAsc();

    @org.springframework.data.jpa.repository.Query("""
            select new com.smartfix.facility.dto.LocationFacilityCount(f.location.id, f.status, count(f))
            from Facility f where f.location.active = true
            group by f.location.id, f.status
            """)
    List<com.smartfix.facility.dto.LocationFacilityCount> countActiveByLocationAndStatus();
}
