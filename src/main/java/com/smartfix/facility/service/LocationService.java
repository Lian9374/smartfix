package com.smartfix.facility.service;

import com.smartfix.common.exception.InputValidationException;
import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.facility.domain.Location;
import com.smartfix.facility.dto.LocationResponse;
import com.smartfix.facility.repository.LocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class LocationService {

    private final LocationRepository locationRepository;

    public LocationService(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    public List<LocationResponse> listActiveLocations() {
        return locationRepository
            .findAllByActiveTrueOrderByDisplayNameAsc()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    public LocationResponse getLocation(Long locationId) {
        Location location = locationRepository.findById(locationId)
            .orElseThrow(() ->
                new ResourceNotFoundException("Location not found"));

        return toResponse(location);
    }

    public Location requireActiveLocation(Long locationId) {
        Location location = locationRepository.findById(locationId)
            .orElseThrow(() ->
                new InputValidationException("Invalid location"));

        if (!location.isActive()) {
            throw new InputValidationException("Location is not active");
        }

        return location;
    }

    private LocationResponse toResponse(Location location) {
        return new LocationResponse(
            location.getId(),
            location.getLocationCode(),
            location.getBuilding(),
            location.getFloor(),
            location.getRoom(),
            location.getDisplayName(),
            location.isActive()
        );
    }
}
