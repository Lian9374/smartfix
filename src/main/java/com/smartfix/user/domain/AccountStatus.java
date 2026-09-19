package com.smartfix.user.domain;

/**
 * Whether an account may currently be used.
 *
 * <p>This is deliberately <strong>not</strong> a role and deliberately not a general
 * account lifecycle. Sprint 2 only needs "can this account sign in and act":</p>
 *
 * <ul>
 *   <li>{@link #ACTIVE} - the account may sign in and be used.</li>
 *   <li>{@link #DISABLED} - the account may not be used. An existing session is
 *       invalidated because disabling an account bumps
 *       {@code User.securityVersion} (plan section 12.1, AC09).</li>
 * </ul>
 *
 * <p>States such as {@code LOCKED}, {@code EXPIRED}, {@code PENDING} or
 * {@code DELETED} are intentionally not modelled: they belong to requirements that
 * do not exist yet, and a status enum is cheap to extend later but expensive to
 * guess at now.</p>
 */
public enum AccountStatus {
    ACTIVE,
    DISABLED
}
