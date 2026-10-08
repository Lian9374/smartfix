package com.smartfix.dispatch.dto;

import jakarta.validation.constraints.*;

public record WithdrawAssignmentCommand(@NotNull @Positive Long expectedAssignmentId,
        @NotBlank @Size(max = 500) String reason) { }
