package com.smartfix.user.dto;

import com.smartfix.user.domain.User;
import com.smartfix.user.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The public "create a requester account" form.
 *
 * <p>A mutable bean rather than a record because Spring MVC binds it from a form and
 * Thymeleaf renders it back with {@code th:field} when validation fails - the same
 * reason {@link CreateUserCommand} is one.</p>
 *
 * <p><strong>It carries no role and no account status, and that is the point.</strong>
 * {@link CreateUserCommand}, the administrator's form, has a {@code role} field because
 * an administrator chooses one; this form is filled in by the person the account is for,
 * so the only thing they may choose is who they are. The role is fixed in
 * {@code UserService.registerRequester} and the status is fixed by
 * {@link User#create} - neither is reachable from a submitted field, so a POST that adds
 * {@code role=ADMINISTRATOR} or {@code accountStatus=DISABLED} is not rejecting anything:
 * there is no binding target for it to land in.</p>
 *
 * <p>Deliberately also absent: {@code id}, {@code securityVersion}, {@code passwordHash},
 * {@code createdAt}, {@code updatedAt} and every audit field. All of them are decided by
 * the server.</p>
 *
 * <p>There is no email address, because {@code users} has no such column and nothing in
 * the product needs one yet: adding a field to the account model to fill a registration
 * form would be the form deciding what an account is.</p>
 */
public class RegistrationCommand {

    /**
     * Matched case-insensitively, like the administrator's form: "Alice" is a legitimate
     * thing to type and is normalized to "alice" rather than refused. The authoritative
     * rule runs against the normalized value in the service, and the unique index is what
     * settles a race between two people typing the same name.
     */
    @NotBlank(message = "Username is required.")
    @Pattern(regexp = "(?i)" + User.USERNAME_PATTERN,
            message = "Username must be 3-50 characters using only letters, digits, '.', '_' or '-'.")
    private String username;

    @NotBlank(message = "Display name is required.")
    @Size(max = User.DISPLAY_NAME_MAX_LENGTH,
            message = "Display name must be at most " + User.DISPLAY_NAME_MAX_LENGTH + " characters.")
    private String displayName;

    /** Plain text only for the length of this call; hashed before anything is persisted. */
    @NotBlank(message = "Password is required.")
    @ValidPassword
    private String password;

    /**
     * Never reaches the database and is never compared to a stored value: it exists to
     * catch a typo in a field the author cannot read back.
     *
     * <p>It carries no {@code @ValidPassword}: the policy is a rule about the password,
     * and reporting the same failure twice - once under each field - would say the
     * confirmation is weak when it is only different.</p>
     */
    @NotBlank(message = "Confirm your password.")
    private String confirmPassword;

    public RegistrationCommand() {
        // form binding
    }

    /**
     * @return whether the two submitted secrets are equal. {@code null} never matches,
     *         not even another {@code null}: both fields are required, and a form that
     *         sent neither must not be read as a confirmation.
     */
    public boolean passwordsMatch() {
        return password != null && password.equals(confirmPassword);
    }

    /**
     * Drops both submitted secrets so that a refused submission cannot render them back.
     *
     * <p>The template never binds either field with {@code th:field}, which is already
     * enough to keep them out of the HTML; this is the second, unconditional guarantee, so
     * that the rule does not depend on a future edit to the markup. It is called after
     * validation has read the values, never before.</p>
     */
    public void clearSecrets() {
        this.password = null;
        this.confirmPassword = null;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    /**
     * Never includes either submitted secret.
     *
     * <p>Written by hand rather than generated from the fields for the same reason
     * {@code User.toString} omits its hash: this string reaches logs and debuggers, and a
     * plain password there is not a hash to crack but the password itself.</p>
     */
    @Override
    public String toString() {
        return "RegistrationCommand{username='" + username + '\''
                + ", displayName='" + displayName + '\''
                + '}';
    }
}
