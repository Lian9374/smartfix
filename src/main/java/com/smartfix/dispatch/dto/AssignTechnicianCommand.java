package com.smartfix.dispatch.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** technicianId is the account id, never the profile id. Actor identity is a separate service argument. */
public record AssignTechnicianCommand(@NotNull @Positive Long technicianId) { }
