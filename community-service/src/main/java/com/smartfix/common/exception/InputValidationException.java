package com.smartfix.common.exception;

/**
 * Input passed bean-validation but violates a rule that needs more than the field
 * itself - a normalized value, a cross-field rule, or a rule shared with the database.
 * Mapped to <strong>400</strong> by {@code GlobalExceptionHandler}.
 *
 * <p>Examples: a username that only becomes too short after trimming, or a password
 * that fails the frozen policy.</p>
 *
 * <p>Category B owns this class and the handler that maps it. The message is shown to
 * the user, so it must describe the rule and never echo the rejected value - above all
 * never the password.</p>
 */
public class InputValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InputValidationException(String message) {
        super(message);
    }
}
