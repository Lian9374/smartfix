package com.smartfix.facility.service;

import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.facility.domain.Facility;
import com.smartfix.facility.domain.FacilityStatus;
import com.smartfix.facility.domain.Location;
import com.smartfix.facility.repository.FacilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FacilityServiceTest {

    private FacilityRepository facilityRepository;
    private Clock clock;
    private FacilityService facilityService;

    @BeforeEach
    void setUp() {
        facilityRepository = mock(FacilityRepository.class);

        clock = Clock.fixed(
            Instant.parse("2026-10-06T08:00:00Z"),
            ZoneOffset.UTC);

        facilityService = new FacilityService(
            facilityRepository,
            clock);
    }

    @Test
    void changeStatusUpdatesOnlyFacilityState() {
        Facility facility = mock(Facility.class);
        Location location = mock(Location.class);

        when(facilityRepository.findById(1L))
            .thenReturn(Optional.of(facility));

        when(facility.getLocation())
            .thenReturn(location);

        when(location.getId())
            .thenReturn(10L);

        when(location.getDisplayName())
            .thenReturn("COM1 Level 1");

        facilityService.changeStatus(
            1L,
            FacilityStatus.OUT_OF_SERVICE);

        ArgumentCaptor<java.time.OffsetDateTime> changedAt =
            ArgumentCaptor.forClass(java.time.OffsetDateTime.class);

        verify(facility).changeStatus(
            org.mockito.ArgumentMatchers.eq(
                FacilityStatus.OUT_OF_SERVICE),
            changedAt.capture());

        assertEquals(
            Instant.parse("2026-10-06T08:00:00Z"),
            changedAt.getValue().toInstant());
    }

    @Test
    void changeStatusRejectsUnknownFacility() {
        when(facilityRepository.findById(999L))
            .thenReturn(Optional.empty());

        assertThrows(
            ResourceNotFoundException.class,
            () -> facilityService.changeStatus(
                999L,
                FacilityStatus.UNDER_MAINTENANCE));
    }

    @Test
    void changeStatusRejectsNullStatus() {
        assertThrows(
            NullPointerException.class,
            () -> facilityService.changeStatus(
                1L,
                null));
    }
}
