package com.smartfix.user.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Applies {@link PasswordPolicy} to a submitted password.
 *
 * <p>Delegates every decision to {@code PasswordPolicy} and only reports the outcome:
 * this class exists to connect that rule to Bean Validation, not to hold a second copy
 * of it.</p>
 */
public class PasswordConstraintValidator implements ConstraintValidator<ValidPassword, String> {

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            // Absence is @NotBlank's responsibility, so a missing password is not
            // reported twice.
            return true;
        }
        String failure = PasswordPolicy.describeFailure(password);
        if (failure == null) {
            return true;
        }
        // Replace the generic default with the rule that actually failed. The template
        // is a literal message from PasswordPolicy, so nothing user-supplied is
        // interpolated into it.
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(failure).addConstraintViolation();
        return false;
    }
}
