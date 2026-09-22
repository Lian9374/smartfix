package com.smartfix.common.exception;

/**
 * The request is well formed but conflicts with the current state of the system.
 * Mapped to <strong>409</strong> by {@code GlobalExceptionHandler}.
 *
 * <p>Examples: creating an account whose username is already taken (AC06), and trying
 * to disable or demote the last active administrator.</p>
 *
 * <p>Category B owns this class and the handler that maps it. The message is written
 * to be shown to a signed-in administrator, so it must be actionable and free of SQL,
 * stack traces, file paths and any credential.</p>
 */
public class BusinessConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BusinessConflictException(String message) {
        super(message);
    }
}
