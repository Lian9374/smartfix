package com.smartfix.request.dto;

import com.smartfix.request.domain.MaintenanceCategory;
import com.smartfix.request.domain.MaintenanceRequest;
import com.smartfix.request.domain.UrgencyLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Browser-submitted fields for a new maintenance request.
 *
 * <p>Deliberately absent are {@code requesterId}, {@code ticketNumber}, {@code status}
 * and timestamps. Those values are derived exclusively on the server.</p>
 */
public class SubmitMaintenanceRequestCommand {

    @NotNull(message = "Location is required.")
    private Long locationId;

    @NotBlank(message = "Title is required.")
    @Size(max = MaintenanceRequest.TITLE_MAX_LENGTH,
            message = "Title must be at most " + MaintenanceRequest.TITLE_MAX_LENGTH + " characters.")
    private String title;

    @NotBlank(message = "Description is required.")
    @Size(max = MaintenanceRequest.DESCRIPTION_MAX_LENGTH,
            message = "Description must be at most "
                    + MaintenanceRequest.DESCRIPTION_MAX_LENGTH + " characters.")
    private String description;

    @NotNull(message = "Category is required.")
    private MaintenanceCategory category;

    @NotNull(message = "Urgency level is required.")
    private UrgencyLevel urgencyLevel;

    public SubmitMaintenanceRequestCommand() {
        // form binding
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public MaintenanceCategory getCategory() {
        return category;
    }

    public void setCategory(MaintenanceCategory category) {
        this.category = category;
    }

    public UrgencyLevel getUrgencyLevel() {
        return urgencyLevel;
    }

    public void setUrgencyLevel(UrgencyLevel urgencyLevel) {
        this.urgencyLevel = urgencyLevel;
    }

    @Override
    public String toString() {
        return "SubmitMaintenanceRequestCommand{locationId=" + locationId
                + ", title='" + title + '\''
                + ", category=" + category
                + ", urgencyLevel=" + urgencyLevel
                + '}';
    }
}
