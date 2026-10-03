package com.smartfix.request.dto;

import jakarta.validation.constraints.*;

public class RequestLifecycleActionCommand {
    @Size(max = 500)
    private String comment;

    public String getComment() {
        return comment;
    }

    public void setComment(String value) {
        comment = value;
    }
}
