package com.smartfix.user.dto;

import com.smartfix.user.domain.Role;
import jakarta.validation.constraints.NotNull;

/**
 * The "change this account's role" form.
 *
 * <p>Carries only the new role. The acting administrator's id is never submitted - the
 * controller takes it from the authenticated principal (plan section 12, principle 1).</p>
 */
public class ChangeUserRoleCommand {

    @NotNull(message = "Role is required.")
    private Role role;

    public ChangeUserRoleCommand() {
        // form binding
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    @Override
    public String toString() {
        return "ChangeUserRoleCommand{role=" + role + '}';
    }
}
