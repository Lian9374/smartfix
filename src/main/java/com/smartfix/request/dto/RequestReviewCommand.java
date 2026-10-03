package com.smartfix.request.dto;

import com.smartfix.request.domain.UrgencyLevel;

import jakarta.validation.constraints.*;

public class RequestReviewCommand {
    @NotNull private UrgencyLevel finalUrgencyLevel;

    public UrgencyLevel getFinalUrgencyLevel() {
        return finalUrgencyLevel;
    }

    public void setFinalUrgencyLevel(UrgencyLevel value) {
        finalUrgencyLevel = value;
    }

    private boolean reject;

    public boolean getReject() {
        return reject;
    }

    public void setReject(boolean value) {
        reject = value;
    }

    @Size(max = 500)
    private String comment;

    public String getComment() {
        return comment;
    }

    public void setComment(String value) {
        comment = value;
    }
}
