package com.smartfix.user.domain;

/**
 * Official system roles defined by the approved SmartFix project proposal.
 *
 * <p>Only the three officially approved roles exist at this stage. A separate
 * "FACILITY_OFFICER" role is intentionally <strong>not</strong> modelled: if the role
 * model is decomposed later it will be introduced as a documented requirement change,
 * not as a scaffold assumption.</p>
 *
 * <p>This enum is a minimal domain placeholder. The full user/role model (accounts,
 * activation, role assignment) is designed and implemented in Sprint 2.</p>
 */
public enum Role {
    REQUESTER,
    TECHNICIAN,
    ADMINISTRATOR
}
