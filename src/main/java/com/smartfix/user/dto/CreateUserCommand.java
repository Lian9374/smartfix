package com.smartfix.user.dto;

import com.smartfix.user.domain.Role;
import com.smartfix.user.domain.User;
import com.smartfix.user.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The "create an account" form.
 *
 * <p>A mutable bean rather than a record because Spring MVC binds it from a form and
 * Thymeleaf renders it back with {@code th:field} when validation fails.</p>
 *
 * <p>It carries exactly what an administrator is allowed to choose. Deliberately
 * absent: {@code accountStatus} (a new account is always {@code ACTIVE}), and
 * {@code securityVersion}, {@code passwordHash}, {@code createdAt}, {@code updatedAt}
 * and the acting administrator's id - all of which are decided by the server, never
 * submitted by the browser (plan section 10.1, "client may submit").</p>
 */
public class CreateUserCommand {

    /**
     * Matched case-insensitively on purpose: "Alice" is a legitimate thing to type and
     * is normalized to "alice" rather than rejected. The authoritative rule - applied
     * to the normalized value - lives in the service, and the database check constraint
     * backs it up.
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

    @NotNull(message = "Role is required.")
    private Role role;

    public CreateUserCommand() {
        // form binding
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

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    /** Never includes the password, so this is safe to log. */
    @Override
    public String toString() {
        return "CreateUserCommand{username='" + username + '\''
                + ", displayName='" + displayName + '\''
                + ", role=" + role
                + '}';
    }
}
