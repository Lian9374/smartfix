package com.smartfix.request.domain;

import java.util.Arrays;
import java.util.List;

/** Five operational queues; persisted states retain their history and exceptional outcomes. */
public enum RequestStage {
    TO_ASSIGN("To assign", "Review the request and choose an engineer",
            RequestStatus.SUBMITTED, RequestStatus.UNDER_REVIEW),
    REPAIR("Repair", "Assigned to an engineer or work in progress",
            RequestStatus.ASSIGNED, RequestStatus.IN_PROGRESS, RequestStatus.REOPENED),
    USER_REVIEW("User review", "Repair submitted; waiting for the requester",
            RequestStatus.RESOLVED),
    ADMIN_REVIEW("Final review", "Requester accepted; administrator approval needed",
            RequestStatus.CONFIRMED),
    FINISHED("Finished", "Closed, declined or cancelled requests",
            RequestStatus.CLOSED, RequestStatus.REJECTED, RequestStatus.CANCELLED);

    private final String label;
    private final String description;
    private final List<RequestStatus> statuses;

    RequestStage(String label, String description, RequestStatus... statuses) {
        this.label = label;
        this.description = description;
        this.statuses = List.of(statuses);
    }

    public String getLabel() { return label; }
    public String getDescription() { return description; }
    public List<RequestStatus> getStatuses() { return statuses; }

    public static RequestStage forStatus(RequestStatus status) {
        return Arrays.stream(values()).filter(stage -> stage.statuses.contains(status))
                .findFirst().orElseThrow();
    }
}
