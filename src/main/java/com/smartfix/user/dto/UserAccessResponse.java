package com.smartfix.user.dto;

import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;

/**
 * The access context other modules need in order to make an authorisation decision.
 *
 * <p>Category D reads it in {@code RequestAccessService} to decide whether the caller
 * may see a request; categories C and D also use it to confirm an account is still
 * {@link AccountStatus#ACTIVE} before acting.</p>
 *
 * <p>Carries only what such a decision needs. Deliberately absent: {@code passwordHash}
 * (obviously) and {@code displayName}, which no authorisation rule requires - a smaller
 * contract is a smaller thing to misuse (plan section 13.6).</p>
 *
 * @param userId          account id
 * @param role            one of the three official roles
 * @param accountStatus   whether the account may currently be used
 * @param securityVersion current session-invalidation counter
 */
public record UserAccessResponse(
        Long userId,
        Role role,
        AccountStatus accountStatus,
        long securityVersion) {
}
