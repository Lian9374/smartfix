package com.smartfix.facility.dto;

import com.smartfix.facility.domain.FacilityStatus;

/** Counts only: map polling never needs facility descriptions or complete entities. */
public record LocationFacilityCount(Long locationId, FacilityStatus status, long count) {}
