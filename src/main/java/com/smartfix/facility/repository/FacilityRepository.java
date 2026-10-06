package com.smartfix.facility.repository;

import com.smartfix.facility.domain.Facility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FacilityRepository extends JpaRepository<Facility, Long> {

    List<Facility> findAllByOrderByNameAsc();

    List<Facility> findAllByLocationIdOrderByNameAsc(Long locationId);
}
