package com.smartfix.request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class FinalReviewCommand {
    @NotBlank(message = "Explain what still needs fixing.")
    @Size(max = 500)
    private String comment;

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
