package com.smartfix.workorder.dto;

import jakarta.validation.constraints.*;

public class CompleteWorkOrderCommand {
    @NotBlank(message = "Enter a solution before submitting.")
    @Size(max = 500, message = "Solution must be at most 500 characters.")
    private String resolutionNote;

    public String getResolutionNote() {
        return resolutionNote;
    }

    public void setResolutionNote(String value) {
        resolutionNote = value;
    }
}
