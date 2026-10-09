package com.smartfix.announcement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

public class CreateAnnouncementCommand {

    @NotBlank
    @Size(max = 150)
    private String title;

    @NotBlank
    @Size(max = 4000)
    private String content;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime validFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime validTo;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDateTime validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDateTime getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDateTime validTo) {
        this.validTo = validTo;
    }

    @AssertTrue(message = "Valid until must be later than valid from.")
    public boolean isValidPeriod() {
        return validFrom == null
            || validTo == null
            || validTo.isAfter(validFrom);
    }
}
