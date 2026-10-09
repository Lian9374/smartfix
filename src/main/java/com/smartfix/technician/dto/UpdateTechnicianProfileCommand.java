package com.smartfix.technician.dto;

import com.smartfix.request.domain.MaintenanceCategory;
import com.smartfix.technician.domain.AvailabilityStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.LinkedHashSet;
import java.util.Set;

/** No user id or active flag: a technician can only edit their own preferences. */
public class UpdateTechnicianProfileCommand {
    @NotEmpty(message = "Select at least one skill.")
    private Set<@NotNull MaintenanceCategory> skills = new LinkedHashSet<>();

    @NotEmpty(message = "Select at least one service area.")
    private Set<@NotNull @Positive Long> serviceAreaIds = new LinkedHashSet<>();

    @NotNull(message = "Select your availability.")
    private AvailabilityStatus availabilityStatus;

    @PositiveOrZero
    private Long version;

    public Set<MaintenanceCategory> getSkills() { return skills; }
    public void setSkills(Set<MaintenanceCategory> skills) { this.skills = skills; }
    public Set<Long> getServiceAreaIds() { return serviceAreaIds; }
    public void setServiceAreaIds(Set<Long> serviceAreaIds) { this.serviceAreaIds = serviceAreaIds; }
    public AvailabilityStatus getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(AvailabilityStatus status) { this.availabilityStatus = status; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
