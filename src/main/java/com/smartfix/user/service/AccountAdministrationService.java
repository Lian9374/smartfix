package com.smartfix.user.service;

import com.smartfix.audit.service.AuditService;
import com.smartfix.user.domain.*;
import com.smartfix.user.dto.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

/** Privileged application boundary: controller identity, active role and transactional audit. */
@Service
@Transactional
public class AccountAdministrationService {
    private final UserService users;
    private final AuditService audit;
    public AccountAdministrationService(UserService users, AuditService audit) {
        this.users = users; this.audit = audit;
    }
    public Long createUser(CreateUserCommand command, Long actorId) {
        requireAdministrator(actorId);
        Long id = users.createUser(command, actorId);
        users.requireManagedPasswordChange(id);
        record(actorId, "ACCOUNT_CREATED", id);
        return id;
    }
    public void changeRole(Long id, ChangeUserRoleCommand command, Long actorId) {
        requireAdministrator(actorId); users.changeRole(id, command, actorId); record(actorId, "ACCOUNT_ROLE_CHANGED", id);
    }
    public void changeAccountStatus(Long id, ChangeAccountStatusCommand command, Long actorId) {
        requireAdministrator(actorId); users.changeAccountStatus(id, command, actorId); record(actorId, "ACCOUNT_STATUS_CHANGED", id);
    }
    public void resetPassword(Long id, Long actorId, String adminPassword, String password, String confirmation) {
        requireAdministrator(actorId);
        users.resetManagedPassword(id, actorId, adminPassword, password, confirmation);
        record(actorId, "ACCOUNT_PASSWORD_RESET", id);
    }
    private void requireAdministrator(Long id) {
        if (id == null) throw new AccessDeniedException("Administrator access required.");
        var actor = users.getUserAccess(id);
        if (actor.role() != Role.ADMINISTRATOR || actor.accountStatus() != AccountStatus.ACTIVE || actor.passwordChangeRequired())
            throw new AccessDeniedException("Administrator access required.");
    }
    private void record(Long actor, String action, Long target) {
        audit.record(actor, action, "USER", target, "SUCCESS", Instant.now());
    }
}
