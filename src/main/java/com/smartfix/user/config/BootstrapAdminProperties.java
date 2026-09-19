package com.smartfix.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the first administrator account.
 *
 * <p>Bound from {@code smartfix.bootstrap-admin.*}. Every value is expected to arrive
 * from the environment, so {@code SMARTFIX_BOOTSTRAP_ADMIN_USERNAME} and so on map onto
 * these properties without any explicit wiring:</p>
 *
 * <pre>
 * smartfix.bootstrap-admin.enabled      &lt;- SMARTFIX_BOOTSTRAP_ADMIN_ENABLED
 * smartfix.bootstrap-admin.username     &lt;- SMARTFIX_BOOTSTRAP_ADMIN_USERNAME
 * smartfix.bootstrap-admin.password     &lt;- SMARTFIX_BOOTSTRAP_ADMIN_PASSWORD
 * smartfix.bootstrap-admin.display-name &lt;- SMARTFIX_BOOTSTRAP_ADMIN_DISPLAY_NAME
 * </pre>
 *
 * <p>Enabled defaults to {@code false}, so nothing is created unless somebody asks for
 * it. A real password must never be written into this repository - not into Java, a
 * migration, {@code application.yml}, a Compose file or a README. The environment is
 * the only place these values belong.</p>
 */
@Component
@ConfigurationProperties(prefix = "smartfix.bootstrap-admin")
public class BootstrapAdminProperties {

    /** Whether to try to create the first administrator during startup. */
    private boolean enabled;

    /** Login name for the first administrator. */
    private String username;

    /** Plain password for the first administrator. Supplied by the environment only. */
    private String password;

    /** Display name; falls back to the username when not supplied. */
    private String displayName;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Reports whether the feature is on and a username is present, without ever
     * revealing either password or username value.
     */
    @Override
    public String toString() {
        return "BootstrapAdminProperties{enabled=" + enabled
                + ", usernameConfigured=" + (username != null && !username.isBlank())
                + ", passwordConfigured=" + (password != null && !password.isBlank())
                + '}';
    }
}
