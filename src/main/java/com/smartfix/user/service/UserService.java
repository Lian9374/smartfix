package com.smartfix.user.service;

import com.smartfix.common.exception.BusinessConflictException;
import com.smartfix.common.exception.InputValidationException;
import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.domain.User;
import com.smartfix.user.dto.ChangeAccountStatusCommand;
import com.smartfix.user.dto.ChangeUserRoleCommand;
import com.smartfix.user.dto.CreateUserCommand;
import com.smartfix.user.dto.UserAccessResponse;
import com.smartfix.user.dto.UserAuthenticationData;
import com.smartfix.user.dto.UserSummaryResponse;
import com.smartfix.user.repository.UserRepository;
import com.smartfix.user.validation.PasswordPolicy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Accounts and roles: the authoritative source of who exists, in what role, and whether
 * they may currently be used.
 *
 * <p>Not split into {@code IUserService} and {@code UserServiceImpl}. There is one
 * implementation and no plan for a second, so an interface would only add a file to
 * keep in step (plan section 8.2).</p>
 *
 * <p>The two rules worth knowing before changing anything here:</p>
 * <ol>
 *   <li><strong>A role or status change bumps {@code securityVersion}</strong>, which is
 *       how category B notices that a session issued earlier is no longer valid
 *       (AC09). The bump lives in {@link User}, so no caller can forget it.</li>
 *   <li><strong>The last active administrator cannot be disabled or demoted.</strong>
 *       Otherwise a single mistake locks every administrator out of the system, with no
 *       route back in short of editing the database by hand.</li>
 * </ol>
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates an account.
     *
     * @param command     the validated creation form
     * @param actorUserId the administrator performing the action. Taken from the
     *                    authenticated principal by the controller, never from the form.
     *                    Sprint 2 has no audit table, so it is not yet persisted; it is
     *                    part of the contract so that adding the audit trail later does
     *                    not change every call site.
     * @return the id of the new account
     * @throws InputValidationException  if the username or display name is invalid once
     *                                   normalized, or the password fails the policy
     * @throws BusinessConflictException if the username is already taken
     */
    @Transactional
    public Long createUser(CreateUserCommand command, Long actorUserId) {
        return createAccount(command).getId();
    }

    /**
     * The bootstrap path: creates the very first administrator.
     *
     * <p>Separate from {@link #createUser} rather than reusing it, because at this point
     * nobody is signed in and there is no administrator to name as the actor. Passing a
     * fabricated id such as {@code 1L} would put a lie in the audit trail before the
     * audit trail even exists, so this method records no actor and says so.</p>
     *
     * <p>Idempotent by contract: if an account with this username already exists, nothing
     * is created and nothing is overwritten. That is what makes it safe to call on every
     * startup (AC for D17).</p>
     *
     * @param command must request {@link Role#ADMINISTRATOR}
     * @return {@code true} if the account was created, {@code false} if it already existed
     */
    @Transactional
    public boolean createInitialAdministrator(CreateUserCommand command) {
        if (command.getRole() != Role.ADMINISTRATOR) {
            throw new IllegalArgumentException("The bootstrap account must be an ADMINISTRATOR");
        }
        String username = User.normalizeUsername(command.getUsername());
        if (username != null && userRepository.existsByUsername(username)) {
            return false;
        }
        createAccount(command);
        return true;
    }

    /**
     * Changes an account's role.
     *
     * <p>Idempotent: asking for the role it already has succeeds and changes nothing, so
     * it neither bumps {@code securityVersion} nor trips the last-administrator rule.</p>
     *
     * @throws ResourceNotFoundException if no such account exists
     * @throws BusinessConflictException if this would remove the last active administrator
     */
    @Transactional
    public void changeRole(Long userId, ChangeUserRoleCommand command, Long actorUserId) {
        User user = requireUser(userId);
        Role newRole = command.getRole();
        if (user.getRole() == newRole) {
            return;
        }
        if (user.isActiveAdministrator()) {
            requireAnotherActiveAdministratorExists(userId);
        }
        user.changeRole(newRole, Instant.now());
    }

    /**
     * Enables or disables an account.
     *
     * <p>Idempotent in the same way as {@link #changeRole}. Disabling an active
     * administrator is the dangerous direction and is the only one that needs the
     * last-administrator check; enabling somebody can only ever add an administrator.</p>
     *
     * @throws ResourceNotFoundException if no such account exists
     * @throws BusinessConflictException if this would disable the last active administrator
     */
    @Transactional
    public void changeAccountStatus(Long userId, ChangeAccountStatusCommand command, Long actorUserId) {
        User user = requireUser(userId);
        AccountStatus newStatus = command.getAccountStatus();
        if (user.getAccountStatus() == newStatus) {
            return;
        }
        if (newStatus == AccountStatus.DISABLED && user.isActiveAdministrator()) {
            requireAnotherActiveAdministratorExists(userId);
        }
        user.changeAccountStatus(newStatus, Instant.now());
    }

    /** @return every account, oldest first, as listing rows that carry no credential */
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> listUsers() {
        return userRepository.findAllByOrderByIdAsc().stream()
                .map(UserService::toSummary)
                .toList();
    }

    /**
     * Loads the data category B needs in order to authenticate a username.
     *
     * <p>The failure message is deliberately generic. Reporting "no such username"
     * separately from "wrong password" would turn the login form into an oracle for
     * which accounts exist (AC03), so category B must answer both cases identically.</p>
     *
     * @param username the submitted login name, in any case
     * @throws ResourceNotFoundException if no account has that username
     */
    @Transactional(readOnly = true)
    public UserAuthenticationData findAuthenticationByUsername(String username) {
        String normalized = User.normalizeUsername(username);
        Optional<User> found = normalized == null
                ? Optional.empty()
                : userRepository.findByUsername(normalized);
        User user = found.orElseThrow(
                () -> new ResourceNotFoundException("No account matches the supplied credentials."));
        return new UserAuthenticationData(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getRole(),
                user.getAccountStatus(),
                user.getSecurityVersion());
    }

    /**
     * Loads the access context categories C and D need in order to authorise an action.
     *
     * @throws ResourceNotFoundException if no such account exists
     */
    @Transactional(readOnly = true)
    public UserAccessResponse getUserAccess(Long userId) {
        User user = requireUser(userId);
        return new UserAccessResponse(
                user.getId(),
                user.getRole(),
                user.getAccountStatus(),
                user.getSecurityVersion());
    }

    /**
     * Shared account creation, used by both the administrator path and the bootstrap
     * path. Both must apply the same normalization, the same password policy and the
     * same encoding; duplicating that for the bootstrap would mean two definitions of a
     * valid account that could drift apart.
     */
    private User createAccount(CreateUserCommand command) {
        String username = User.normalizeUsername(command.getUsername());
        if (!User.isValidUsername(username)) {
            throw new InputValidationException(
                    "Username must be 3-50 characters using only letters, digits, '.', '_' or '-'.");
        }
        String displayName = command.getDisplayName() == null ? null : command.getDisplayName().trim();
        if (!User.isValidDisplayName(displayName)) {
            throw new InputValidationException(
                    "Display name must be 1-" + User.DISPLAY_NAME_MAX_LENGTH + " characters.");
        }
        String passwordFailure = PasswordPolicy.describeFailure(command.getPassword());
        if (passwordFailure != null) {
            throw new InputValidationException(passwordFailure);
        }
        if (userRepository.existsByUsername(username)) {
            throw new BusinessConflictException("An account with this username already exists.");
        }

        String passwordHash = passwordEncoder.encode(command.getPassword());
        User user = User.create(username, displayName, passwordHash, command.getRole(), Instant.now());
        try {
            // Flushed here rather than at commit so that a duplicate username is caught
            // while this method can still translate it. The existsByUsername check above
            // is a courtesy that produces a better message; it cannot be trusted under
            // concurrency, so the unique constraint is what actually guarantees
            // uniqueness.
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // The service has already validated every other rule, so a constraint
            // violation on this insert can only realistically be the username.
            throw new BusinessConflictException("An account with this username already exists.");
        }
    }

    /**
     * Refuses the change unless some other account is still an active administrator.
     *
     * <p>The read is not a plain count. It locks every active administrator row, which
     * makes two administrators demoting each other serialize: the second transaction
     * waits, then sees the first one's committed result and is refused. A plain count
     * would let both observe two actives and both proceed, leaving the system with no
     * administrator at all.</p>
     *
     * <p>The lock is always taken first and nothing else is locked afterwards, so there
     * is a single lock point and no lock-ordering cycle to deadlock on.</p>
     */
    private void requireAnotherActiveAdministratorExists(Long userIdBeingChanged) {
        boolean anotherExists = userRepository
                .findForUpdateByRoleAndAccountStatus(Role.ADMINISTRATOR, AccountStatus.ACTIVE)
                .stream()
                .anyMatch(candidate -> !Objects.equals(candidate.getId(), userIdBeingChanged));
        if (!anotherExists) {
            throw new BusinessConflictException(
                    "The last active administrator cannot be disabled or demoted. "
                            + "Promote or enable another administrator first.");
        }
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account " + userId + " does not exist."));
    }

    private static UserSummaryResponse toSummary(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole(),
                user.getAccountStatus(),
                user.getSecurityVersion(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
