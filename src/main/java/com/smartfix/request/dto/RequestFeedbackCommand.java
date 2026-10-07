package com.smartfix.request.dto;

import jakarta.validation.constraints.*;

public class RequestFeedbackCommand {
    @NotNull
    @Min(1)
    @Max(5)
    private Integer rating;

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer value) {
        rating = value;
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
