package com.smartfix.request.domain;

/**
 * Lifecycle state of a maintenance request.
 *
 * <p>Sprint 2 implements request submission only, so {@link #SUBMITTED} is the
 * only valid state. Future states must be introduced with their transition rules,
 * database constraint changes and status-history tests in the same reviewed change.</p>
 */
public enum RequestStatus {
    SUBMITTED
}
