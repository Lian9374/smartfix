package com.smartfix.technician.domain;

import com.smartfix.request.domain.MaintenanceCategory;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Technician-owned preferences; account identity and location data remain in their own modules. */
@Entity
@Table(name = "technician_profiles")
public class TechnicianProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_status", nullable = false, length = 20)
    private AvailabilityStatus availabilityStatus;

    @Column(nullable = false)
    private boolean active;

    @Version
    @Column(nullable = false)
    private long version;

    @ElementCollection
    @CollectionTable(name = "technician_skills", joinColumns = @JoinColumn(name = "technician_profile_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private Set<MaintenanceCategory> skills = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "technician_service_areas", joinColumns = @JoinColumn(name = "technician_profile_id"))
    @Column(name = "location_id", nullable = false)
    private Set<Long> serviceAreaIds = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TechnicianProfile() { }

    public static TechnicianProfile create(Long userId, Set<MaintenanceCategory> skills,
            Set<Long> serviceAreaIds, AvailabilityStatus availability, Instant now) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("A valid user id is required.");
        }
        TechnicianProfile profile = new TechnicianProfile();
        profile.userId = userId;
        profile.active = true;
        profile.createdAt = Objects.requireNonNull(now, "now");
        profile.updatePreferences(skills, serviceAreaIds, availability, now);
        return profile;
    }

    public void updatePreferences(Set<MaintenanceCategory> skills, Set<Long> serviceAreaIds,
            AvailabilityStatus availability, Instant now) {
        if (skills == null || skills.isEmpty() || skills.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Select at least one skill.");
        }
        if (serviceAreaIds == null || serviceAreaIds.isEmpty()
                || serviceAreaIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Select at least one service area.");
        }
        Objects.requireNonNull(availability, "availability");
        Objects.requireNonNull(now, "now");
        // Validate and copy before mutating; callers may supply this entity's own collections.
        Set<MaintenanceCategory> newSkills = Set.copyOf(skills);
        Set<Long> newAreas = Set.copyOf(serviceAreaIds);
        this.skills.clear();
        this.skills.addAll(newSkills);
        this.serviceAreaIds.clear();
        this.serviceAreaIds.addAll(newAreas);
        this.availabilityStatus = availability;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public AvailabilityStatus getAvailabilityStatus() { return availabilityStatus; }
    public boolean isActive() { return active; }
    public long getVersion() { return version; }
    public Set<MaintenanceCategory> getSkills() { return Set.copyOf(skills); }
    public Set<Long> getServiceAreaIds() { return Set.copyOf(serviceAreaIds); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
