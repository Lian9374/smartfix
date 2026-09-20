package com.smartfix.auth.security;

import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.UserAuthenticationData;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

/** Identity captured at login. No entity or repository is kept in the session. */
public final class SmartFixUserDetails implements UserDetails, CredentialsContainer {
    @Serial
    private static final long serialVersionUID = 1L;
    private final Long userId;
    private final String username;
    private transient String passwordHash;
    private final Role role;
    private final AccountStatus accountStatus;
    private final long securityVersion;

    public SmartFixUserDetails(UserAuthenticationData data) {
        this.userId = data.userId();
        this.username = data.username();
        this.passwordHash = data.passwordHash();
        this.role = data.role();
        this.accountStatus = data.accountStatus();
        this.securityVersion = data.securityVersion();
    }

    public Long getUserId() { return userId; }
    public Role getRole() { return role; }
    public long getSecurityVersion() { return securityVersion; }

    @Override
    public String getUsername() { return username; }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public boolean isEnabled() { return accountStatus == AccountStatus.ACTIVE; }

    @Override
    public void eraseCredentials() { passwordHash = null; }

    @Override
    public String toString() {
        return "SmartFixUserDetails{userId=" + userId + ", role=" + role + '}';
    }
}
