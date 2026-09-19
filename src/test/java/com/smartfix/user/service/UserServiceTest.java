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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserService}.
 *
 * <p>The repository and the encoder are mocked, so these run against no database at all
 * and stay fast. What matters here is the service's own behaviour: normalization, the
 * password policy, the {@code securityVersion} invariant, and the rule that the system
 * can never be left without an active administrator.</p>
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String RAW_PASSWORD = "CorrectHorse1";
    private static final String BCRYPT_HASH = "$2a$10$abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQ";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("creates an account and returns its id")
        void createsAccount() {
            stubSuccessfulCreation();

            Long createdId = userService.createUser(command("alice", "Alice Tan", Role.REQUESTER), 99L);

            assertThat(createdId).isEqualTo(1L);
            verify(userRepository).saveAndFlush(any(User.class));
        }

        @Test
        @DisplayName("trims and lower-cases the username so one person cannot become three accounts")
        void normalizesUsername() {
            stubSuccessfulCreation();

            userService.createUser(command("  ALICE  ", "Alice Tan", Role.REQUESTER), 99L);

            assertThat(capturedSavedUser().getUsername()).isEqualTo("alice");
        }

        @Test
        @DisplayName("looks up duplicates by the normalized username")
        void checksDuplicatesUsingTheNormalizedUsername() {
            when(userRepository.existsByUsername("alice")).thenReturn(false);
            stubEncodingAndSave();

            userService.createUser(command("ALICE", "Alice Tan", Role.REQUESTER), 99L);

            verify(userRepository).existsByUsername("alice");
        }

        @Test
        @DisplayName("rejects a username that is already taken with a business conflict")
        void rejectsDuplicateUsername() {
            when(userRepository.existsByUsername("alice")).thenReturn(true);

            assertThatThrownBy(() -> userService.createUser(command("alice", "Alice Tan", Role.REQUESTER), 99L))
                    .isInstanceOf(BusinessConflictException.class);

            verify(userRepository, never()).saveAndFlush(any(User.class));
        }

        @Test
        @DisplayName("still rejects a duplicate when the check passes but the unique constraint fires")
        void translatesUniqueConstraintViolation() {
            // The pre-check is a courtesy for a better message; it cannot be trusted under
            // concurrency, so the database constraint must be what actually decides.
            when(userRepository.existsByUsername("alice")).thenReturn(false);
            when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(BCRYPT_HASH);
            when(userRepository.saveAndFlush(any(User.class)))
                    .thenThrow(new DataIntegrityViolationException("uk_users_username"));

            assertThatThrownBy(() -> userService.createUser(command("alice", "Alice Tan", Role.REQUESTER), 99L))
                    .isInstanceOf(BusinessConflictException.class);
        }

        @Test
        @DisplayName("stores a BCrypt hash and never the plain password")
        void storesHashNotPlainText() {
            stubSuccessfulCreation();

            userService.createUser(command("alice", "Alice Tan", Role.REQUESTER), 99L);

            verify(passwordEncoder).encode(RAW_PASSWORD);
            User saved = capturedSavedUser();
            assertThat(saved.getPasswordHash()).isEqualTo(BCRYPT_HASH);
            assertThat(saved.getPasswordHash()).isNotEqualTo(RAW_PASSWORD);
        }

        @Test
        @DisplayName("a new account is ACTIVE with securityVersion 0")
        void newAccountDefaults() {
            stubSuccessfulCreation();

            userService.createUser(command("alice", "Alice Tan", Role.REQUESTER), 99L);

            User saved = capturedSavedUser();
            assertThat(saved.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(saved.getSecurityVersion()).isZero();
        }

        @ParameterizedTest
        @EnumSource(Role.class)
        @DisplayName("can create an account in each of the three official roles")
        void createsEveryRole(Role role) {
            stubSuccessfulCreation();

            userService.createUser(command("alice", "Alice Tan", role), 99L);

            assertThat(capturedSavedUser().getRole()).isEqualTo(role);
        }

        @Test
        @DisplayName("rejects a username that is too short only after trimming")
        void rejectsUsernameThatIsTooShortAfterTrimming() {
            // "  ab  " satisfies @Size(min = 3) on the raw form value, so the service is
            // the layer that has to catch it.
            assertThatThrownBy(() -> userService.createUser(command("  ab  ", "Alice Tan", Role.REQUESTER), 99L))
                    .isInstanceOf(InputValidationException.class);

            verify(userRepository, never()).saveAndFlush(any(User.class));
        }

        @Test
        @DisplayName("rejects a display name that is blank after trimming")
        void rejectsBlankDisplayName() {
            assertThatThrownBy(() -> userService.createUser(command("alice", "   ", Role.REQUESTER), 99L))
                    .isInstanceOf(InputValidationException.class);

            verify(userRepository, never()).saveAndFlush(any(User.class));
        }

        @Nested
        @DisplayName("password policy")
        class PasswordPolicyEnforcement {

            @Test
            @DisplayName("rejects a password shorter than the minimum, without encoding it")
            void rejectsShortPassword() {
                assertThatThrownBy(() -> userService.createUser(
                        command("alice", "Alice Tan", Role.REQUESTER, "Short1"), 99L))
                        .isInstanceOf(InputValidationException.class);

                verify(passwordEncoder, never()).encode(anyString());
            }

            @Test
            @DisplayName("rejects a password with no digit")
            void rejectsPasswordWithoutDigit() {
                assertThatThrownBy(() -> userService.createUser(
                        command("alice", "Alice Tan", Role.REQUESTER, "NoDigitsHereAtAll"), 99L))
                        .isInstanceOf(InputValidationException.class);
            }

            @Test
            @DisplayName("rejects a password with no letter")
            void rejectsPasswordWithoutLetter() {
                assertThatThrownBy(() -> userService.createUser(
                        command("alice", "Alice Tan", Role.REQUESTER, "1234567890123"), 99L))
                        .isInstanceOf(InputValidationException.class);
            }

            @Test
            @DisplayName("rejects a password over BCrypt's 72-byte limit even when it is short in characters")
            void rejectsPasswordOverTheByteLimit() {
                // 30 characters, but four bytes each: 120 bytes, past what BCrypt reads.
                String multiBytePassword = "Passw0rd" + "中".repeat(22);

                assertThatThrownBy(() -> userService.createUser(
                        command("alice", "Alice Tan", Role.REQUESTER, multiBytePassword), 99L))
                        .isInstanceOf(InputValidationException.class);

                verify(passwordEncoder, never()).encode(anyString());
            }

            @Test
            @DisplayName("the failure message never echoes the submitted password")
            void failureMessageDoesNotLeakThePassword() {
                String secret = "leak-me-not";

                assertThatThrownBy(() -> userService.createUser(
                        command("alice", "Alice Tan", Role.REQUESTER, secret), 99L))
                        .isInstanceOf(InputValidationException.class)
                        .hasMessageNotContaining(secret);
            }
        }
    }

    @Nested
    @DisplayName("changeRole")
    class ChangeRole {

        @Test
        @DisplayName("changes the role and bumps securityVersion so existing sessions die")
        void changesRoleAndInvalidatesSessions() {
            User user = persistedUser(1L, "alice", Role.REQUESTER, AccountStatus.ACTIVE, 0L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            userService.changeRole(1L, roleCommand(Role.TECHNICIAN), 99L);

            assertThat(user.getRole()).isEqualTo(Role.TECHNICIAN);
            assertThat(user.getSecurityVersion()).isEqualTo(1L);
        }

        @Test
        @DisplayName("does not lock administrators when the target is not one")
        void doesNotConsultAdministratorsForANonAdministrator() {
            User user = persistedUser(1L, "alice", Role.REQUESTER, AccountStatus.ACTIVE, 0L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            userService.changeRole(1L, roleCommand(Role.TECHNICIAN), 99L);

            verify(userRepository, never()).findForUpdateByRoleAndAccountStatus(any(), any());
        }

        @Test
        @DisplayName("re-submitting the current role is a no-op and does not bump securityVersion")
        void sameRoleIsIdempotent() {
            User user = persistedUser(1L, "alice", Role.TECHNICIAN, AccountStatus.ACTIVE, 3L);
            Instant updatedAtBefore = user.getUpdatedAt();
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            userService.changeRole(1L, roleCommand(Role.TECHNICIAN), 99L);

            assertThat(user.getSecurityVersion()).isEqualTo(3L);
            assertThat(user.getUpdatedAt()).isEqualTo(updatedAtBefore);
        }

        @Test
        @DisplayName("promoting a non-administrator never consults the administrator rule")
        void promotingIsAlwaysAllowed() {
            User user = persistedUser(1L, "alice", Role.REQUESTER, AccountStatus.ACTIVE, 0L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            userService.changeRole(1L, roleCommand(Role.ADMINISTRATOR), 99L);

            assertThat(user.getRole()).isEqualTo(Role.ADMINISTRATOR);
        }

        @Test
        @DisplayName("refuses to demote the last active administrator")
        void refusesToDemoteTheLastAdministrator() {
            User onlyAdministrator = persistedUser(1L, "root", Role.ADMINISTRATOR, AccountStatus.ACTIVE, 0L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(onlyAdministrator));
            when(userRepository.findForUpdateByRoleAndAccountStatus(Role.ADMINISTRATOR, AccountStatus.ACTIVE))
                    .thenReturn(List.of(onlyAdministrator));

            assertThatThrownBy(() -> userService.changeRole(1L, roleCommand(Role.REQUESTER), 99L))
                    .isInstanceOf(BusinessConflictException.class);

            assertThat(onlyAdministrator.getRole()).isEqualTo(Role.ADMINISTRATOR);
            assertThat(onlyAdministrator.getSecurityVersion()).isZero();
        }

        @Test
        @DisplayName("allows demoting an administrator when a second active one exists")
        void allowsDemotingWhenAnotherAdministratorExists() {
            User first = persistedUser(1L, "root", Role.ADMINISTRATOR, AccountStatus.ACTIVE, 0L);
            User second = persistedUser(2L, "backup", Role.ADMINISTRATOR, AccountStatus.ACTIVE, 0L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(first));
            when(userRepository.findForUpdateByRoleAndAccountStatus(Role.ADMINISTRATOR, AccountStatus.ACTIVE))
                    .thenReturn(List.of(first, second));

            userService.changeRole(1L, roleCommand(Role.REQUESTER), 99L);

            assertThat(first.getRole()).isEqualTo(Role.REQUESTER);
        }

        @Test
        @DisplayName("a disabled administrator does not count as cover for demoting the active one")
        void disabledAdministratorIsNotCover() {
            User active = persistedUser(1L, "root", Role.ADMINISTRATOR, AccountStatus.ACTIVE, 0L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(active));
            // The query only ever returns ACTIVE administrators, so the disabled one is
            // simply not in the result.
            when(userRepository.findForUpdateByRoleAndAccountStatus(Role.ADMINISTRATOR, AccountStatus.ACTIVE))
                    .thenReturn(List.of(active));

            assertThatThrownBy(() -> userService.changeRole(1L, roleCommand(Role.TECHNICIAN), 99L))
                    .isInstanceOf(BusinessConflictException.class);
        }

        @Test
        @DisplayName("an unknown account is a controlled not-found, not a crash")
        void unknownAccountIsNotFound() {
            when(userRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.changeRole(404L, roleCommand(Role.TECHNICIAN), 99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("changeAccountStatus")
    class ChangeAccountStatus {

        @Test
        @DisplayName("disables an account and bumps securityVersion so existing sessions die")
        void disablesAndInvalidatesSessions() {
            User user = persistedUser(1L, "alice", Role.REQUESTER, AccountStatus.ACTIVE, 0L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            userService.changeAccountStatus(1L, statusCommand(AccountStatus.DISABLED), 99L);

            assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.DISABLED);
            assertThat(user.getSecurityVersion()).isEqualTo(1L);
        }

        @Test
        @DisplayName("re-submitting the current status is a no-op and does not bump securityVersion")
        void sameStatusIsIdempotent() {
            User user = persistedUser(1L, "alice", Role.REQUESTER, AccountStatus.DISABLED, 4L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            userService.changeAccountStatus(1L, statusCommand(AccountStatus.DISABLED), 99L);

            assertThat(user.getSecurityVersion()).isEqualTo(4L);
        }

        @Test
        @DisplayName("enabling a disabled account never needs the administrator rule")
        void enablingIsAlwaysAllowed() {
            User user = persistedUser(1L, "root", Role.ADMINISTRATOR, AccountStatus.DISABLED, 7L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            userService.changeAccountStatus(1L, statusCommand(AccountStatus.ACTIVE), 99L);

            assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
            verify(userRepository, never()).findForUpdateByRoleAndAccountStatus(any(), any());
        }

        @Test
        @DisplayName("refuses to disable the last active administrator")
        void refusesToDisableTheLastAdministrator() {
            User onlyAdministrator = persistedUser(1L, "root", Role.ADMINISTRATOR, AccountStatus.ACTIVE, 0L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(onlyAdministrator));
            when(userRepository.findForUpdateByRoleAndAccountStatus(Role.ADMINISTRATOR, AccountStatus.ACTIVE))
                    .thenReturn(List.of(onlyAdministrator));

            assertThatThrownBy(() -> userService.changeAccountStatus(1L, statusCommand(AccountStatus.DISABLED), 99L))
                    .isInstanceOf(BusinessConflictException.class);

            assertThat(onlyAdministrator.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        @DisplayName("allows disabling an administrator when a second active one exists")
        void allowsDisablingWhenAnotherAdministratorExists() {
            User first = persistedUser(1L, "root", Role.ADMINISTRATOR, AccountStatus.ACTIVE, 0L);
            User second = persistedUser(2L, "backup", Role.ADMINISTRATOR, AccountStatus.ACTIVE, 0L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(first));
            when(userRepository.findForUpdateByRoleAndAccountStatus(Role.ADMINISTRATOR, AccountStatus.ACTIVE))
                    .thenReturn(List.of(first, second));

            userService.changeAccountStatus(1L, statusCommand(AccountStatus.DISABLED), 99L);

            assertThat(first.getAccountStatus()).isEqualTo(AccountStatus.DISABLED);
        }

        @Test
        @DisplayName("an unknown account is a controlled not-found, not a crash")
        void unknownAccountIsNotFound() {
            when(userRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.changeAccountStatus(404L, statusCommand(AccountStatus.DISABLED), 99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("queries")
    class Queries {

        @Test
        @DisplayName("listUsers never carries a password hash")
        void listUsersCarriesNoSecret() {
            when(userRepository.findAllByOrderByIdAsc())
                    .thenReturn(List.of(persistedUser(1L, "alice", Role.REQUESTER, AccountStatus.ACTIVE, 0L)));

            List<UserSummaryResponse> users = userService.listUsers();

            assertThat(users).hasSize(1);
            assertThat(users.get(0).username()).isEqualTo("alice");
            assertThat(users.get(0).toString()).doesNotContain(BCRYPT_HASH);
            assertThat(users.get(0).toString().toLowerCase()).doesNotContain("password");
        }

        @Test
        @DisplayName("findAuthenticationByUsername returns everything category B needs")
        void findAuthenticationReturnsTheFullContract() {
            User user = persistedUser(1L, "alice", Role.TECHNICIAN, AccountStatus.ACTIVE, 5L);
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

            UserAuthenticationData data = userService.findAuthenticationByUsername("ALICE");

            assertThat(data.userId()).isEqualTo(1L);
            assertThat(data.username()).isEqualTo("alice");
            assertThat(data.passwordHash()).isEqualTo(BCRYPT_HASH);
            assertThat(data.role()).isEqualTo(Role.TECHNICIAN);
            assertThat(data.accountStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(data.securityVersion()).isEqualTo(5L);
        }

        @Test
        @DisplayName("the authentication payload redacts the hash when printed")
        void authenticationPayloadRedactsTheHash() {
            User user = persistedUser(1L, "alice", Role.TECHNICIAN, AccountStatus.ACTIVE, 5L);
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

            UserAuthenticationData data = userService.findAuthenticationByUsername("alice");

            // The compiler-generated record toString would have printed the hash; a hash in
            // a log file is a credential an attacker can crack offline.
            assertThat(data.toString()).doesNotContain(BCRYPT_HASH);
            assertThat(data.toString()).contains("REDACTED");
        }

        @Test
        @DisplayName("an unknown username is reported as a controlled not-found")
        void unknownUsernameIsNotFound() {
            when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.findAuthenticationByUsername("nobody"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("getUserAccess returns the access context without any credential")
        void getUserAccessCarriesNoSecret() {
            User user = persistedUser(1L, "alice", Role.REQUESTER, AccountStatus.ACTIVE, 2L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            UserAccessResponse access = userService.getUserAccess(1L);

            assertThat(access.userId()).isEqualTo(1L);
            assertThat(access.role()).isEqualTo(Role.REQUESTER);
            assertThat(access.accountStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(access.securityVersion()).isEqualTo(2L);
            assertThat(access.toString()).doesNotContain(BCRYPT_HASH);
            assertThat(access.toString().toLowerCase()).doesNotContain("password");
        }

        @Test
        @DisplayName("an unknown user id is a controlled not-found")
        void unknownUserIdIsNotFound() {
            when(userRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUserAccess(404L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("createInitialAdministrator")
    class CreateInitialAdministrator {

        @Test
        @DisplayName("creates the administrator when no such account exists")
        void createsWhenAbsent() {
            stubSuccessfulCreation();

            boolean created = userService.createInitialAdministrator(
                    command("root", "Root", Role.ADMINISTRATOR));

            assertThat(created).isTrue();
            assertThat(capturedSavedUser().getRole()).isEqualTo(Role.ADMINISTRATOR);
        }

        @Test
        @DisplayName("does nothing when the account already exists, so a restart is harmless")
        void skipsWhenPresent() {
            when(userRepository.existsByUsername("root")).thenReturn(true);

            boolean created = userService.createInitialAdministrator(
                    command("root", "Root", Role.ADMINISTRATOR));

            assertThat(created).isFalse();
            verify(userRepository, never()).saveAndFlush(any(User.class));
        }

        @Test
        @DisplayName("refuses to be used for any role other than administrator")
        void refusesNonAdministrator() {
            assertThatThrownBy(() -> userService.createInitialAdministrator(
                    command("root", "Root", Role.REQUESTER)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ---------------------------------------------------------------- helpers

    private void stubSuccessfulCreation() {
        stubEncodingAndSave();
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
    }

    private void stubEncodingAndSave() {
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(BCRYPT_HASH);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        });
    }

    private User capturedSavedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private static CreateUserCommand command(String username, String displayName, Role role) {
        return command(username, displayName, role, RAW_PASSWORD);
    }

    private static CreateUserCommand command(String username, String displayName, Role role, String password) {
        CreateUserCommand command = new CreateUserCommand();
        command.setUsername(username);
        command.setDisplayName(displayName);
        command.setPassword(password);
        command.setRole(role);
        return command;
    }

    private static ChangeUserRoleCommand roleCommand(Role role) {
        ChangeUserRoleCommand command = new ChangeUserRoleCommand();
        command.setRole(role);
        return command;
    }

    private static ChangeAccountStatusCommand statusCommand(AccountStatus status) {
        ChangeAccountStatusCommand command = new ChangeAccountStatusCommand();
        command.setAccountStatus(status);
        return command;
    }

    /**
     * Builds a {@link User} that looks like it came back from the database, including the
     * generated id and security version, which the factory deliberately does not assign.
     */
    private static User persistedUser(Long id, String username, Role role, AccountStatus status, long securityVersion) {
        User user = User.create(username, "Display " + username, BCRYPT_HASH, role, Instant.parse("2026-01-01T00:00:00Z"));
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "accountStatus", status);
        ReflectionTestUtils.setField(user, "securityVersion", securityVersion);
        if (securityVersion > 0L) {
            ReflectionTestUtils.setField(user, "updatedAt", Instant.parse("2026-02-01T00:00:00Z"));
        }
        return user;
    }
}
