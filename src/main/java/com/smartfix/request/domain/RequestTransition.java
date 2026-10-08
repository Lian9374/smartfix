package com.smartfix.request.domain;

import com.smartfix.user.domain.Role;

import java.util.List;

/** Sprint 3 baseline (T01–T13). Ownership and prerequisites are checked by the service. */
public final class RequestTransition {
    public record Rule(RequestStatus from, RequestStatus to, Role role) {}

    private static final List<Rule> RULES =
            List.of(
                    rule(RequestStatus.SUBMITTED, RequestStatus.UNDER_REVIEW, Role.ADMINISTRATOR),
                    rule(RequestStatus.SUBMITTED, RequestStatus.CANCELLED, Role.REQUESTER),
                    rule(RequestStatus.UNDER_REVIEW, RequestStatus.CANCELLED, Role.REQUESTER),
                    rule(RequestStatus.UNDER_REVIEW, RequestStatus.ASSIGNED, Role.ADMINISTRATOR),
                    rule(RequestStatus.UNDER_REVIEW, RequestStatus.REJECTED, Role.ADMINISTRATOR),
                    rule(RequestStatus.ASSIGNED, RequestStatus.IN_PROGRESS, Role.TECHNICIAN),
                    rule(RequestStatus.ASSIGNED, RequestStatus.UNDER_REVIEW, Role.ADMINISTRATOR),
                    rule(RequestStatus.IN_PROGRESS, RequestStatus.ASSIGNED, Role.ADMINISTRATOR),
                    rule(RequestStatus.IN_PROGRESS, RequestStatus.RESOLVED, Role.TECHNICIAN),
                    rule(RequestStatus.RESOLVED, RequestStatus.CONFIRMED, Role.REQUESTER),
                    rule(RequestStatus.CONFIRMED, RequestStatus.CLOSED, Role.ADMINISTRATOR),
                    rule(RequestStatus.RESOLVED, RequestStatus.REOPENED, Role.REQUESTER),
                    rule(RequestStatus.CONFIRMED, RequestStatus.REOPENED, Role.REQUESTER),
                    rule(RequestStatus.REOPENED, RequestStatus.ASSIGNED, Role.ADMINISTRATOR),
                    rule(RequestStatus.REOPENED, RequestStatus.IN_PROGRESS, Role.TECHNICIAN));

    private RequestTransition() {}

    private static Rule rule(RequestStatus from, RequestStatus to, Role role) {
        return new Rule(from, to, role);
    }

    public static boolean allows(RequestStatus from, RequestStatus to, Role role) {
        return RULES.contains(new Rule(from, to, role));
    }

    public static List<Rule> rules() {
        return RULES;
    }
}
