package com.smartfix.user.service;

import com.smartfix.user.config.BootstrapAdminProperties;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.CreateUserCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Creates the very first administrator so that a fresh deployment has somebody who can
 * sign in.
 *
 * <p>Without this, a new database has no accounts at all, nobody can log in, and nobody
 * can create anybody - the system would be unreachable by design. This is the one
 * sanctioned way in, and it is deliberately narrow: off unless explicitly enabled,
 * sourced only from the environment, idempotent, and it never changes an account that
 * already exists.</p>
 *
 * <p>It owns no rules of its own. The username is normalized, the password is checked
 * against the policy and encoded, and the account is created all by
 * {@link UserService#createInitialAdministrator} - the same code an administrator's own
 * "create account" goes through. Duplicating that here would give "valid account" two
 * definitions that could drift.</p>
 */
@Service
public class UserBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(UserBootstrapService.class);

    private static final String ENABLED_PROPERTY = "smartfix.bootstrap-admin.enabled";

    private final UserService userService;
    private final BootstrapAdminProperties properties;

    public UserBootstrapService(UserService userService, BootstrapAdminProperties properties) {
        this.userService = userService;
        this.properties = properties;
    }

    /**
     * Creates the bootstrap administrator if the feature is enabled and the account does
     * not exist yet.
     *
     * <p>Safe to run on every startup: the second run finds the account and does nothing.
     * An existing account is never updated - not its password, not its role, not its
     * status - so a later change made by an administrator is not silently undone by a
     * restart.</p>
     *
     * @throws IllegalStateException if the feature is enabled but a required value is
     *                               missing. Failing loudly at startup is deliberate:
     *                               the alternative is a deployment that looks healthy
     *                               but has no way in.
     */
    public void initialize() {
        if (!properties.isEnabled()) {
            log.debug("Bootstrap administrator creation is disabled ({} = false).", ENABLED_PROPERTY);
            return;
        }

        CreateUserCommand command = buildCommand();
        boolean created = userService.createInitialAdministrator(command);
        if (created) {
            log.info("Created the bootstrap administrator account '{}'.", command.getUsername());
        } else {
            log.info("Bootstrap administrator account '{}' already exists; nothing was changed.",
                    command.getUsername());
        }
    }

    /**
     * Assembles the creation command from configuration.
     *
     * <p>The password is passed through exactly as supplied and never trimmed: leading or
     * trailing spaces are legitimate password characters, and silently altering them
     * would produce an account whose password is not the one that was configured. It
     * stays in plain text only until {@code UserService} encodes it.</p>
     */
    private CreateUserCommand buildCommand() {
        String username = requireConfigured(
                properties.getUsername(), "username", "SMARTFIX_BOOTSTRAP_ADMIN_USERNAME").trim();
        String password = requireConfigured(
                properties.getPassword(), "password", "SMARTFIX_BOOTSTRAP_ADMIN_PASSWORD");

        String configuredDisplayName = properties.getDisplayName();
        String displayName = (configuredDisplayName == null || configuredDisplayName.isBlank())
                ? username
                : configuredDisplayName.trim();

        CreateUserCommand command = new CreateUserCommand();
        command.setUsername(username);
        command.setDisplayName(displayName);
        command.setPassword(password);
        command.setRole(Role.ADMINISTRATOR);
        return command;
    }

    /**
     * @return the configured value
     * @throws IllegalStateException naming the missing property and the environment
     *                               variable that supplies it. The message never
     *                               contains a configured value, so it is safe to log.
     */
    private String requireConfigured(String value, String propertyName, String environmentVariable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "The property '" + ENABLED_PROPERTY + "' is true but 'smartfix.bootstrap-admin."
                            + propertyName + "' is not set. Provide it through the "
                            + environmentVariable + " environment variable.");
        }
        return value;
    }
}
