package com.smartfix.facility.service;

import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.facility.domain.Facility;
import com.smartfix.facility.domain.FacilityStatus;
import com.smartfix.facility.dto.FacilityResponse;
import com.smartfix.facility.repository.FacilityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class FacilityService {

    private final FacilityRepository facilityRepository;
    private final Clock clock;

    public FacilityService(
        FacilityRepository facilityRepository,
        Clock clock) {

        this.facilityRepository = facilityRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<FacilityResponse> listFacilities() {
        return facilityRepository.findAllByOrderByNameAsc()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public FacilityResponse getFacility(Long facilityId) {
        return toResponse(requireFacility(facilityId));
    }

    @Transactional
    public FacilityResponse changeStatus(
        Long facilityId,
        FacilityStatus newStatus) {

        Objects.requireNonNull(newStatus, "newStatus must not be null");

        Facility facility = requireFacility(facilityId);

        facility.changeStatus(
            newStatus,
            OffsetDateTime.now(clock));

        return toResponse(facility);
    }

    private Facility requireFacility(Long facilityId) {
        return facilityRepository.findById(facilityId)
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    "Facility not found: " + facilityId));
    }

    private FacilityResponse toResponse(Facility facility) {
        return new FacilityResponse(
            facility.getId(),
            facility.getLocation().getId(),
            facility.getLocation().getDisplayName(),
            facility.getName(),
            facility.getDescription(),
            facility.getStatus(),
            facility.getCreatedAt(),
            facility.getUpdatedAt());
    }
}
