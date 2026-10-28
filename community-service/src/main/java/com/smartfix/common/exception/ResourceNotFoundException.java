package com.smartfix.common.exception;

/**
 * A requested resource does not exist, or the caller is not allowed to know that it
 * does. Mapped to <strong>404</strong> by {@code GlobalExceptionHandler}.
 *
 * <p>The 404 is also how ownership is enforced (plan section 13.4): a request that
 * belongs to somebody else is reported as "not found" rather than "forbidden", so an
 * attacker cannot use the difference to enumerate which ticket numbers exist.</p>
 *
 * <p>Category B owns this class and the handler that maps it. The message must never
 * carry SQL, a stack trace or a file path, because it may end up on a page.</p>
 */
public class ResourceNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
