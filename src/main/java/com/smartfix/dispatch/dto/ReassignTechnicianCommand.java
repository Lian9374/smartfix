package com.smartfix.dispatch.dto;

import jakarta.validation.constraints.*;

/** expectedAssignmentId prevents a stale administrator form from replacing a newer assignment. */
public record ReassignTechnicianCommand(@NotNull @Positive Long technicianId,
        @NotNull @Positive Long expectedAssignmentId, @NotBlank @Size(max = 500) String reason) { }
