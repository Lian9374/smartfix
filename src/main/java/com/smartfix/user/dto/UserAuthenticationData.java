package com.smartfix.user.dto;

import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;

/**
 * The account data category B needs in order to authenticate somebody.
 *
 * <p>The only type in the whole module that carries {@code passwordHash}. That makes it
 * the most sensitive DTO here, so it is explicitly <strong>not</strong>:</p>
 * <ul>
 *   <li>returned to a browser - no controller may render it;</li>
 *   <li>used for the administrator listing - that is {@link UserSummaryResponse};</li>
 *   <li>logged - {@link #toString()} is redacted for exactly this reason.</li>
 * </ul>
 *
 * <p>Category B consumes it in {@code SmartFixUserDetailsService} to build
 * {@code SmartFixUserDetails}, and compares {@code securityVersion} with the value
 * captured at login to decide whether an existing session is still valid
 * (plan sections 12.1, 13.5).</p>
 *
 * @param userId          account id
 * @param username        normalized login name
 * @param passwordHash    BCrypt hash - never the plain password
 * @param role            one of the three official roles
 * @param accountStatus   a {@link AccountStatus#DISABLED} account must be refused
 * @param securityVersion the value to store in the session and re-check on every request
 */
public record UserAuthenticationData(
        Long userId,
        String username,
        String passwordHash,
        Role role,
        AccountStatus accountStatus,
        long securityVersion) {

    /**
     * Redacted on purpose. The compiler-generated version would print the hash, and a
     * hash in a log file is a credential leak: it is exactly what an attacker needs in
     * order to mount an offline cracking attempt.
     */
    @Override
    public String toString() {
        return "UserAuthenticationData{userId=" + userId
                + ", username='" + username + '\''
                + ", role=" + role
                + ", accountStatus=" + accountStatus
                + ", securityVersion=" + securityVersion
                + ", passwordHash=REDACTED}";
    }
}
