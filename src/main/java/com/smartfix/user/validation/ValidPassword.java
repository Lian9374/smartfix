package com.smartfix.user.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rejects a password that does not satisfy {@link PasswordPolicy}.
 *
 * <p>Bean Validation is the right home for this rule because it needs no collaborator:
 * it is a pure function of the submitted value. Putting it here means a malformed
 * password is refused at the form boundary and never reaches the service, which is what
 * the controller tests assert. {@code UserService} applies the same policy again for
 * callers that do not come through a validated form.</p>
 *
 * <p>{@code null} is considered valid so that the message for a missing password comes
 * from {@code @NotBlank} alone rather than from two annotations at once.</p>
 */
@Documented
@Constraint(validatedBy = PasswordConstraintValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    /**
     * Replaced by the validator with the specific rule that failed, so this default is
     * only a fallback. It never quotes the submitted value.
     */
    String message() default "Password does not meet the required policy.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
