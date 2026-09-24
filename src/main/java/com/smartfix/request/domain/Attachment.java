package com.smartfix.request.domain;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "request_attachments")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private Long requestId;

    @Column(
            name = "original_filename",
            nullable = false,
            length = 255,
            updatable = false
    )
    private String originalFilename;

    @Column(
            name = "stored_filename",
            nullable = false,
            unique = true,
            length = 255,
            updatable = false
    )
    private String storedFilename;

    @Column(
            name = "content_type",
            nullable = false,
            length = 100,
            updatable = false
    )
    private String contentType;

    @Column(
            name = "size_bytes",
            nullable = false,
            updatable = false
    )
    private long sizeBytes;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    protected Attachment() {
        // Required by JPA.
    }

    private Attachment(
            Long requestId,
            String originalFilename,
            String storedFilename,
            String contentType,
            long sizeBytes,
            Instant createdAt
    ) {
        this.requestId = Objects.requireNonNull(requestId);
        this.originalFilename =
                requireText(originalFilename, "originalFilename");
        this.storedFilename =
                requireText(storedFilename, "storedFilename");
        this.contentType =
                requireText(contentType, "contentType");

        if (sizeBytes <= 0) {
            throw new IllegalArgumentException(
                    "sizeBytes must be greater than zero"
            );
        }

        this.sizeBytes = sizeBytes;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static Attachment create(
            Long requestId,
            String originalFilename,
            String storedFilename,
            String contentType,
            long sizeBytes,
            Instant createdAt
    ) {
        return new Attachment(
                requestId,
                originalFilename,
                storedFilename,
                contentType,
                sizeBytes,
                createdAt
        );
    }

    private static String requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }

        return value;
    }

    public Long getId() {
        return id;
    }

    public Long getRequestId() {
        return requestId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getStoredFilename() {
        return storedFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof Attachment attachment)
                || id == null) {
            return false;
        }

        return id.equals(attachment.id);
    }

    @Override
    public int hashCode() {
        return Attachment.class.hashCode();
    }
}