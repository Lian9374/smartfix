package com.smartfix.facility.service;

import com.smartfix.common.exception.InputValidationException;
import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.facility.domain.Location;
import com.smartfix.facility.dto.LocationResponse;
import com.smartfix.facility.repository.LocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocationServiceTest {

    private LocationRepository locationRepository;
    private LocationService locationService;

    @BeforeEach
    void setUp() {
        locationRepository = mock(LocationRepository.class);
        locationService = new LocationService(locationRepository);
    }

    @Test
    void listActiveLocationsReturnsOnlyRepositoryActiveResults() {
        Location location = new Location(
            "COM1-01-01",
            "COM1",
            "01",
            "01",
            "COM1 Level 1 Room 01",
            true
        );

        when(locationRepository
            .findAllByActiveTrueOrderByDisplayNameAsc())
            .thenReturn(List.of(location));

        List<LocationResponse> result =
            locationService.listActiveLocations();

        assertEquals(1, result.size());
        assertEquals(
            "COM1 Level 1 Room 01",
            result.getFirst().displayName()
        );

        verify(locationRepository)
            .findAllByActiveTrueOrderByDisplayNameAsc();

        verify(locationRepository, never()).findAll();
    }

    @Test
    void getLocationThrowsWhenLocationDoesNotExist() {
        when(locationRepository.findById(999L))
            .thenReturn(Optional.empty());

        assertThrows(
            ResourceNotFoundException.class,
            () -> locationService.getLocation(999L)
        );
    }

    @Test
    void requireActiveLocationReturnsActiveLocation() {
        Location location = new Location(
            "COM1-01-01",
            "COM1",
            "01",
            "01",
            "COM1 Level 1 Room 01",
            true
        );

        when(locationRepository.findById(1L))
            .thenReturn(Optional.of(location));

        Location result =
            locationService.requireActiveLocation(1L);

        assertSame(location, result);
    }

    @Test
    void requireActiveLocationRejectsDisabledLocation() {
        Location location = new Location(
            "OLD-01",
            "Old Building",
            null,
            null,
            "Old Building",
            false
        );

        when(locationRepository.findById(2L))
            .thenReturn(Optional.of(location));

        assertThrows(
            InputValidationException.class,
            () -> locationService.requireActiveLocation(2L)
        );
    }

    @Test
    void requireActiveLocationRejectsMissingLocation() {
        when(locationRepository.findById(99L))
            .thenReturn(Optional.empty());

        assertThrows(
            InputValidationException.class,
            () -> locationService.requireActiveLocation(99L)
        );
    }
}
