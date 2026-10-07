package com.smartfix.workorder.dto;

import jakarta.validation.constraints.*;

public class RepairRecordCommand {
    @NotBlank
    @Size(max = 2000)
    private String diagnosis;

    public String getDiagnosis() {
        return diagnosis;
    }

    public void setDiagnosis(String value) {
        diagnosis = value;
    }

    @NotBlank
    @Size(max = 2000)
    private String actionTaken;

    public String getActionTaken() {
        return actionTaken;
    }

    public void setActionTaken(String value) {
        actionTaken = value;
    }

    @Size(max = 1000)
    private String materialsUsed;

    public String getMaterialsUsed() {
        return materialsUsed;
    }

    public void setMaterialsUsed(String value) {
        materialsUsed = value;
    }

    @NotNull
    @Min(1)
    private Integer minutesSpent;

    public Integer getMinutesSpent() {
        return minutesSpent;
    }

    public void setMinutesSpent(Integer value) {
        minutesSpent = value;
    }

    @Positive private Long evidenceAttachmentId;

    public Long getEvidenceAttachmentId() {
        return evidenceAttachmentId;
    }

    public void setEvidenceAttachmentId(Long value) {
        evidenceAttachmentId = value;
    }
}
