package com.smartfix.facility.dto;

import com.smartfix.facility.domain.FacilityStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Existing facility fields only; a facility belongs to an existing campus location. */
public record CreateFacilityCommand(
        @NotNull @Positive Long locationId,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @NotNull FacilityStatus status) { }
