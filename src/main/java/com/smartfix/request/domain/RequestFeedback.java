package com.smartfix.request.domain;

import com.smartfix.common.exception.InputValidationException;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "request_feedback")
public class RequestFeedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, unique = true, updatable = false)
    private Long requestId;

    @Column(name = "requester_id", nullable = false, updatable = false)
    private Long requesterId;

    @Column(nullable = false, updatable = false)
    private int rating;

    @Column(length = 500, updatable = false)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RequestFeedback() {}

    public static RequestFeedback create(
            Long requestId, Long requesterId, int rating, String comment, Instant at) {
        if (rating < 1 || rating > 5)
            throw new InputValidationException("Rating must be between 1 and 5.");
        String note = comment == null ? null : comment.trim();
        if (note != null && note.length() > 500)
            throw new InputValidationException("Comment must be at most 500 characters.");
        RequestFeedback result = new RequestFeedback();
        result.requestId = requestId;
        result.requesterId = requesterId;
        result.rating = rating;
        result.comment = note;
        result.createdAt = at;
        return result;
    }

    public Long getId() {
        return id;
    }

    public Long getRequestId() {
        return requestId;
    }

    public int getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
