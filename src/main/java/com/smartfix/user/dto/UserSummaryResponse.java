package com.smartfix.user.dto;

import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;

import java.time.Instant;

/**
 * One row of the user-management listing.
 *
 * <p>A record, and immutable, because it is only ever read out of the service and
 * rendered. There is no {@code passwordHash} component: this type is shaped for a
 * browser, and a hash must not be one field away from being rendered (plan section
 * 13.4, AC29).</p>
 *
 * @param id              account id
 * @param username        normalized login name
 * @param displayName     human-readable name
 * @param role            one of the three official roles
 * @param accountStatus   whether the account may currently be used
 * @param securityVersion current session-invalidation counter
 * @param createdAt       creation instant, in UTC
 * @param updatedAt       last role/status change, in UTC
 */
public record UserSummaryResponse(
        Long id,
        String username,
        String displayName,
        Role role,
        AccountStatus accountStatus,
        long securityVersion,
        Instant createdAt,
        Instant updatedAt) {
}
