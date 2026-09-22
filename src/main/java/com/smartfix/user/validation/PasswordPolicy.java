package com.smartfix.user.validation;

import java.nio.charset.StandardCharsets;

/**
 * The one definition of an acceptable password.
 *
 * <p>Written once and reached from two places, so the rule cannot drift:</p>
 * <ul>
 *   <li>{@link PasswordConstraintValidator} uses it to reject a bad password at the
 *       form, before any service is called;</li>
 *   <li>{@code UserService} uses it again before encoding, so the rule still holds for
 *       any caller that does not come through a validated form - the bootstrap path,
 *       for example.</li>
 * </ul>
 *
 * <p>The rules are the frozen Sprint 2 baseline (plan section 11): at least
 * {@value #MIN_LENGTH} characters, at least one letter, at least one digit, and at
 * most {@value #MAX_UTF8_BYTES} bytes once encoded as UTF-8. The byte ceiling is not
 * a policy preference - BCrypt silently ignores everything past 72 bytes, so a longer
 * password would be weaker than it looks. It is measured in UTF-8 bytes rather than
 * characters because one character can be up to four bytes.</p>
 *
 * <p>These values are a proposed baseline awaiting the D10 ADR, not an approved
 * requirement.</p>
 */
public final class PasswordPolicy {

    /** Minimum number of characters. */
    public static final int MIN_LENGTH = 12;

    /** Maximum size in UTF-8 bytes - BCrypt's input limit. */
    public static final int MAX_UTF8_BYTES = 72;

    private static final String REQUIRED_MESSAGE = "Password is required.";

    private static final String TOO_SHORT_MESSAGE =
            "Password must be at least " + MIN_LENGTH + " characters long.";

    private static final String TOO_LONG_MESSAGE =
            "Password must be at most " + MAX_UTF8_BYTES + " bytes long when encoded as UTF-8.";

    private static final String TOO_SIMPLE_MESSAGE =
            "Password must contain at least one letter and at least one digit.";

    private PasswordPolicy() {
        // static-only
    }

    /**
     * @param password the plain password under test
     * @return the reason it is unacceptable, or {@code null} when it is acceptable.
     *         Never contains the password itself.
     */
    public static String describeFailure(String password) {
        if (password == null || password.isBlank()) {
            return REQUIRED_MESSAGE;
        }
        if (password.length() < MIN_LENGTH) {
            return TOO_SHORT_MESSAGE;
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES) {
            return TOO_LONG_MESSAGE;
        }
        if (!hasLetterAndDigit(password)) {
            return TOO_SIMPLE_MESSAGE;
        }
        return null;
    }

    /** @return whether the password satisfies every rule */
    public static boolean isSatisfied(String password) {
        return describeFailure(password) == null;
    }

    /**
     * Counts letters and digits in a single pass. Characters outside those two classes
     * (punctuation, spaces) are allowed but do not satisfy either requirement.
     */
    private static boolean hasLetterAndDigit(String password) {
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int index = 0; index < password.length() && !(hasLetter && hasDigit); index++) {
            char character = password.charAt(index);
            if (Character.isLetter(character)) {
                hasLetter = true;
            } else if (Character.isDigit(character)) {
                hasDigit = true;
            }
        }
        return hasLetter && hasDigit;
    }
}
