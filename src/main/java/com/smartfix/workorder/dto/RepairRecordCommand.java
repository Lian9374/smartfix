package com.smartfix.workorder.dto;

import jakarta.validation.constraints.*;

public class RepairRecordCommand {
    @NotBlank(message = "Describe the diagnosis.")
    @Size(max = 2000, message = "Diagnosis must be at most 2,000 characters.")
    private String diagnosis;

    public String getDiagnosis() {
        return diagnosis;
    }

    public void setDiagnosis(String value) {
        diagnosis = value;
    }

    @NotBlank(message = "Describe the work performed.")
    @Size(max = 2000, message = "Work performed must be at most 2,000 characters.")
    private String actionTaken;

    public String getActionTaken() {
        return actionTaken;
    }

    public void setActionTaken(String value) {
        actionTaken = value;
    }

    @Size(max = 1000, message = "Materials must be at most 1,000 characters.")
    private String materialsUsed;

    public String getMaterialsUsed() {
        return materialsUsed;
    }

    public void setMaterialsUsed(String value) {
        materialsUsed = value;
    }

    @NotNull(message = "Enter time spent in minutes.")
    @Min(value = 1, message = "Time spent must be at least 1 minute.")
    private Integer minutesSpent;

    public Integer getMinutesSpent() {
        return minutesSpent;
    }

    public void setMinutesSpent(Integer value) {
        minutesSpent = value;
    }

    @Positive(message = "Choose a valid evidence photo.") private Long evidenceAttachmentId;

    public Long getEvidenceAttachmentId() {
        return evidenceAttachmentId;
    }

    public void setEvidenceAttachmentId(Long value) {
        evidenceAttachmentId = value;
    }
}
