package com.smartfix.request.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Aggregate root for a submitted campus-facility maintenance request.
 *
 * <p>References to the {@code user} and {@code facility} modules are scalar ids,
 * not cross-module JPA associations. The database foreign keys retain referential
 * integrity while the Java modules remain independently maintainable.</p>
 */
@Entity
@Table(name = "maintenance_requests")
public class MaintenanceRequest {

    public static final int TITLE_MAX_LENGTH = 120;
    public static final int DESCRIPTION_MAX_LENGTH = 2000;
    public static final int TICKET_NUMBER_LENGTH = 20;

    private static final Pattern TICKET_NUMBER_PATTERN = Pattern.compile("^SF-[0-9]{4}-[0-9]{6}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_number", nullable = false, unique = true,
            length = TICKET_NUMBER_LENGTH, updatable = false)
    private String ticketNumber;

    @Column(name = "requester_id", nullable = false, updatable = false)
    private Long requesterId;

    @Column(name = "location_id", nullable = false, updatable = false)
    private Long locationId;

    @Column(name = "title", nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(name = "description", nullable = false, length = DESCRIPTION_MAX_LENGTH)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private MaintenanceCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "urgency_level", nullable = false, length = 20)
    private UrgencyLevel urgencyLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RequestStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA. Application code must use {@link #submit}. */
    protected MaintenanceRequest() {
        // no-op
    }

    private MaintenanceRequest(String ticketNumber,
                               Long requesterId,
                               Long locationId,
                               String title,
                               String description,
                               MaintenanceCategory category,
                               UrgencyLevel urgencyLevel,
                               Instant submittedAt) {
        this.ticketNumber = ticketNumber;
        this.requesterId = requesterId;
        this.locationId = locationId;
        this.title = title;
        this.description = description;
        this.category = category;
        this.urgencyLevel = urgencyLevel;
        this.status = RequestStatus.SUBMITTED;
        this.createdAt = submittedAt;
        this.updatedAt = submittedAt;
    }

    /**
     * Creates a request in its only Sprint 2 initial state.
     *
     * <p>{@code requesterId} is supplied by the trusted authenticated principal in
     * the service layer; it is deliberately absent from the browser command DTO.</p>
     */
    public static MaintenanceRequest submit(String ticketNumber,
                                            Long requesterId,
                                            Long locationId,
                                            String title,
                                            String description,
                                            MaintenanceCategory category,
                                            UrgencyLevel urgencyLevel,
                                            Instant submittedAt) {
        String normalizedTicketNumber = requireText(ticketNumber, TICKET_NUMBER_LENGTH, "ticketNumber");
        if (!TICKET_NUMBER_PATTERN.matcher(normalizedTicketNumber).matches()) {
            throw new IllegalArgumentException("ticketNumber must use the SF-YYYY-NNNNNN format");
        }
        String normalizedTitle = requireText(title, TITLE_MAX_LENGTH, "title");
        String normalizedDescription = requireText(description, DESCRIPTION_MAX_LENGTH, "description");
        Objects.requireNonNull(requesterId, "requesterId");
        Objects.requireNonNull(locationId, "locationId");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(urgencyLevel, "urgencyLevel");
        Objects.requireNonNull(submittedAt, "submittedAt");
        return new MaintenanceRequest(
                normalizedTicketNumber,
                requesterId,
                locationId,
                normalizedTitle,
                normalizedDescription,
                category,
                urgencyLevel,
                submittedAt);
    }

    private static String requireText(String value, int maximumLength, String fieldName) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(
                    fieldName + " must contain between 1 and " + maximumLength + " characters");
        }
        return normalized;
    }

    public Long getId() {
        return id;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public Long getRequesterId() {
        return requesterId;
    }

    public Long getLocationId() {
        return locationId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public MaintenanceCategory getCategory() {
        return category;
    }

    public UrgencyLevel getUrgencyLevel() {
        return urgencyLevel;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MaintenanceRequest request) || id == null) {
            return false;
        }
        return id.equals(request.id);
    }

    @Override
    public int hashCode() {
        return MaintenanceRequest.class.hashCode();
    }
}
