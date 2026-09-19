package com.smartfix.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A system account: the authoritative record of who may use SmartFix and in what role.
 *
 * <p>The entity owns its own invariants. Role and status cannot be replaced through
 * public setters - they change only through {@link #changeRole} and
 * {@link #changeAccountStatus}, which keep {@code securityVersion} and
 * {@code updatedAt} consistent with the change. This is what lets category B
 * invalidate an existing session when an administrator changes a role or disables
 * an account (plan section 12.1, AC09).</p>
 *
 * <p>This entity is not a place to hang request data: there is deliberately no
 * collection of maintenance requests here (plan section 5.4 rule 9). Cross-module
 * relationships use scalar ids plus a database foreign key.</p>
 */
@Entity
@Table(name = "users")
public class User {

    /** Minimum length of a normalized username. Mirrored by the database check constraint. */
    public static final int USERNAME_MIN_LENGTH = 3;

    /** Maximum length of a normalized username. Mirrored by {@code users.username VARCHAR(50)}. */
    public static final int USERNAME_MAX_LENGTH = 50;

    /** Maximum length of a display name. Mirrored by {@code users.display_name VARCHAR(100)}. */
    public static final int DISPLAY_NAME_MAX_LENGTH = 100;

    /**
     * The one definition of a valid <em>normalized</em> username, shared by the form
     * constraint, the service and the database check constraint. The pattern is
     * lower-case only: callers must run the raw value through
     * {@link #normalizeUsername(String)} first.
     */
    public static final String USERNAME_PATTERN = "^[a-z0-9._-]{3,50}$";

    private static final Pattern COMPILED_USERNAME_PATTERN = Pattern.compile(USERNAME_PATTERN);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Immutable after creation: there is no rename-account use case in Sprint 2. */
    @Column(name = "username", nullable = false, length = USERNAME_MAX_LENGTH, updatable = false)
    private String username;

    @Column(name = "display_name", nullable = false, length = DISPLAY_NAME_MAX_LENGTH)
    private String displayName;

    /** BCrypt hash. Never the plain password, never rendered, never logged. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus;

    @Column(name = "security_version", nullable = false)
    private long securityVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA. Not for application code - use {@link #create}. */
    protected User() {
        // no-op
    }

    private User(String username, String displayName, String passwordHash, Role role, Instant createdAt) {
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.accountStatus = AccountStatus.ACTIVE;
        this.securityVersion = 0L;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /**
     * Creates a new, usable account.
     *
     * <p>The username is normalized and validated here, so no caller can persist an
     * account whose username violates the shared rule even if it forgot to check.
     * {@code passwordHash} must already be a hash - this class never sees, stores or
     * derives a plain password.</p>
     *
     * @param rawUsername   the submitted username; trimmed and lower-cased
     * @param displayName   the submitted display name; trimmed
     * @param passwordHash  an already-encoded password hash
     * @param role          one of the three official roles
     * @param createdAt     creation instant, in UTC
     * @return a new {@link AccountStatus#ACTIVE} account with {@code securityVersion} 0
     * @throws IllegalArgumentException if any argument is missing or the username is invalid
     */
    public static User create(String rawUsername,
                              String displayName,
                              String passwordHash,
                              Role role,
                              Instant createdAt) {
        String username = normalizeUsername(rawUsername);
        if (!isValidUsername(username)) {
            throw new IllegalArgumentException("Invalid username");
        }
        String trimmedDisplayName = displayName == null ? null : displayName.trim();
        if (!isValidDisplayName(trimmedDisplayName)) {
            throw new IllegalArgumentException("Invalid display name");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("A password hash is required");
        }
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(createdAt, "createdAt");
        return new User(username, trimmedDisplayName, passwordHash, role, createdAt);
    }

    /**
     * The single normalization used everywhere a username enters the system.
     *
     * <p>Without this, {@code "Alice"}, {@code "alice"} and {@code " ALICE "} would be
     * three different accounts to the database. The unique constraint is still the
     * final authority; normalization is what makes it meaningful.</p>
     *
     * @param rawUsername the submitted value, possibly {@code null}
     * @return the trimmed, lower-cased username, or {@code null} if the input was {@code null}
     */
    public static String normalizeUsername(String rawUsername) {
        return rawUsername == null ? null : rawUsername.trim().toLowerCase(Locale.ROOT);
    }

    /** @return whether the value is a valid <em>already normalized</em> username */
    public static boolean isValidUsername(String normalizedUsername) {
        return normalizedUsername != null && COMPILED_USERNAME_PATTERN.matcher(normalizedUsername).matches();
    }

    /** @return whether the value is a valid <em>already trimmed</em> display name */
    public static boolean isValidDisplayName(String trimmedDisplayName) {
        return trimmedDisplayName != null
                && !trimmedDisplayName.isBlank()
                && trimmedDisplayName.length() <= DISPLAY_NAME_MAX_LENGTH;
    }

    /**
     * Changes the role and invalidates existing sessions.
     *
     * <p>Idempotent: submitting the current role changes nothing, so it neither bumps
     * {@code securityVersion} nor raises the "last administrator" rule. Without that,
     * re-saving an administrator with the same role would spuriously fail.</p>
     *
     * @return {@code true} if the role actually changed
     */
    public boolean changeRole(Role newRole, Instant changedAt) {
        Objects.requireNonNull(newRole, "newRole");
        Objects.requireNonNull(changedAt, "changedAt");
        if (this.role == newRole) {
            return false;
        }
        this.role = newRole;
        invalidateSessions(changedAt);
        return true;
    }

    /**
     * Enables or disables the account and invalidates existing sessions.
     *
     * <p>Idempotent in the same way as {@link #changeRole}.</p>
     *
     * @return {@code true} if the status actually changed
     */
    public boolean changeAccountStatus(AccountStatus newStatus, Instant changedAt) {
        Objects.requireNonNull(newStatus, "newStatus");
        Objects.requireNonNull(changedAt, "changedAt");
        if (this.accountStatus == newStatus) {
            return false;
        }
        this.accountStatus = newStatus;
        invalidateSessions(changedAt);
        return true;
    }

    /** @return whether this account is currently an administrator who may sign in */
    public boolean isActiveAdministrator() {
        return this.role == Role.ADMINISTRATOR && this.accountStatus == AccountStatus.ACTIVE;
    }

    /**
     * Replaces the stored hash. Kept out of the public API on purpose: only the
     * service, holding an already-encoded value, may call it, and there is no
     * password-change use case in Sprint 2.
     */
    protected void replacePasswordHash(String newPasswordHash) {
        Objects.requireNonNull(newPasswordHash, "newPasswordHash");
        this.passwordHash = newPasswordHash;
    }

    /**
     * A role or status change must make every session issued before it unusable.
     * Bumping the version is how category B detects that: it compares the version
     * captured at login with the current one on each request.
     */
    private void invalidateSessions(Instant changedAt) {
        this.securityVersion++;
        this.updatedAt = changedAt;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public long getSecurityVersion() {
        return securityVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Deliberately omits {@code passwordHash}: this string reaches logs, debuggers and
     * error reports, and a hash there is a credential leak waiting to be cracked.
     */
    @Override
    public String toString() {
        return "User{id=" + id
                + ", username='" + username + '\''
                + ", role=" + role
                + ", accountStatus=" + accountStatus
                + ", securityVersion=" + securityVersion
                + '}';
    }

    /**
     * Identity is the assigned id. A still-transient entity is never equal to another
     * transient entity, so two distinct new accounts are never collapsed into one.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof User user) || this.id == null) {
            return false;
        }
        return this.id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return User.class.hashCode();
    }
}
