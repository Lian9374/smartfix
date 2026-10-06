package com.smartfix.facility.service;

import com.smartfix.facility.domain.Facility;
import com.smartfix.facility.domain.Location;
import com.smartfix.facility.dto.CampusMapFacilityResponse;
import com.smartfix.facility.repository.FacilityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CampusMapService {

    private final FacilityRepository facilityRepository;

    public CampusMapService(FacilityRepository facilityRepository) {
        this.facilityRepository = facilityRepository;
    }

    @Transactional(readOnly = true)
    public List<CampusMapFacilityResponse> listFacilities() {
        return facilityRepository.findAllByOrderByNameAsc()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    private CampusMapFacilityResponse toResponse(Facility facility) {
        Location location = facility.getLocation();

        return new CampusMapFacilityResponse(
            facility.getId(),
            facility.getName(),
            facility.getStatus(),
            location.getId(),
            location.getDisplayName(),
            location.getBuilding(),
            location.getFloor(),
            location.getRoom());
    }
}
