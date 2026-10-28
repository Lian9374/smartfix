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

    public String getDisplayLabel() {
        return switch (this) {
            case SUBMITTED -> "To assign";
            case UNDER_REVIEW -> "Ready to assign";
            case ASSIGNED -> "Assigned";
            case IN_PROGRESS -> "Repair in progress";
            case RESOLVED -> "Awaiting user review";
            case CONFIRMED -> "Awaiting final review";
            case REOPENED -> "Rework needed";
            case CLOSED -> "Closed";
            case REJECTED -> "Declined";
            case CANCELLED -> "Cancelled";
        };
    }

    public RequestStage getStage() { return RequestStage.forStatus(this); }

    public boolean isTerminal() {
        return this == CLOSED || this == REJECTED || this == CANCELLED;
    }
}
