package com.smartfix.request.domain;

public enum RequestStatus {
    SUBMITTED,
    UNDER_REVIEW,
    ASSIGNED,
    IN_PROGRESS,
    RESOLVED,
    CONFIRMED,
    REOPENED,
    CLOSED,
    REJECTED,
    CANCELLED;

    public boolean isTerminal() {
        return this == CLOSED || this == REJECTED || this == CANCELLED;
    }
}
