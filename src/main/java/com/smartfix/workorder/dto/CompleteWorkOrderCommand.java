package com.smartfix.workorder.dto;

import jakarta.validation.constraints.*;

public class CompleteWorkOrderCommand {
    @NotBlank
    @Size(max = 500)
    private String resolutionNote;

    public String getResolutionNote() {
        return resolutionNote;
    }

    public void setResolutionNote(String value) {
        resolutionNote = value;
    }
}
