package com.smartfix.facility.repository;

import com.smartfix.facility.domain.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    List<Location> findAllByActiveTrueOrderByDisplayNameAsc();

    Optional<Location> findByLocationCode(String locationCode);
}
