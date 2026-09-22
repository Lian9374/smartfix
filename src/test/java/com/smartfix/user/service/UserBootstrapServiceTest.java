package com.smartfix.user.service;

import com.smartfix.common.exception.InputValidationException;
import com.smartfix.user.config.BootstrapAdminProperties;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.domain.User;
import com.smartfix.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for the bootstrap administrator path.
 *
 * <p>Unlike the other service tests, the {@link PasswordEncoder} here is a
 * <strong>real</strong> {@link BCryptPasswordEncoder} rather than a mock. Whether the
 * stored value is genuinely a BCrypt hash - and not, say, the plain password - is the
 * single most important thing about this feature, and a mock would happily agree with
 * any answer. Hashing costs a few tens of milliseconds per account, which is a price
 * worth paying for a test that can actually fail.</p>
 *
 * <p>{@link UserService} is real too, so the idempotence being checked is the real
 * idempotence of the real creation path, not a stub's impression of it.</p>
 */
@ExtendWith(MockitoExtension.class)
class UserBootstrapServiceTest {

    private static final String RAW_PASSWORD = "BootstrapPass1";
    private static final String RAW_USERNAME = "root.admin";

    @Mock
    private UserRepository userRepository;

    private PasswordEncoder passwordEncoder;

    private UserService userService;

    private BootstrapAdminProperties properties;

    private UserBootstrapService bootstrapService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        userService = new UserService(userRepository, passwordEncoder);
        properties = new BootstrapAdminProperties();
        bootstrapService = new UserBootstrapService(userService, properties);
    }

    @Test
    @DisplayName("does nothing at all while the feature is disabled")
    void doesNothingWhenDisabled() {
        properties.setEnabled(false);
        properties.setUsername(RAW_USERNAME);
        properties.setPassword(RAW_PASSWORD);

        bootstrapService.initialize();

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("creates an ADMINISTRATOR account when enabled and configured")
    void createsTheAdministrator() {
        enable();
        stubCreation();

        bootstrapService.initialize();

        User created = captureSaved();
        assertThat(created.getRole()).isEqualTo(Role.ADMINISTRATOR);
        assertThat(created.getUsername()).isEqualTo(RAW_USERNAME);
    }

    @Test
    @DisplayName("the created account is active with securityVersion 0, like any other new account")
    void createdAccountStartsClean() {
        enable();
        stubCreation();

        bootstrapService.initialize();

        User created = captureSaved();
        assertThat(created.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(created.getSecurityVersion()).isZero();
    }

    @Test
    @DisplayName("the stored password is a real BCrypt hash that verifies against the configured password")
    void storesARealBcryptHash() {
        enable();
        stubCreation();

        bootstrapService.initialize();

        String stored = captureSaved().getPasswordHash();

        assertThat(stored)
                .isNotEqualTo(RAW_PASSWORD)
                .startsWith("$2");

        assertThat(passwordEncoder.matches(RAW_PASSWORD, stored))
                .isTrue();
    }

    @Test
    @DisplayName("a second run finds the account and changes nothing, so a restart is harmless")
    void secondRunIsANoOp() {
        enable();
        stubCreation();
        bootstrapService.initialize();

        // The account now exists, which is what the repository would report on a restart.
        when(userRepository.existsByUsername(RAW_USERNAME)).thenReturn(true);
        bootstrapService.initialize();

        verify(userRepository, times(1)).saveAndFlush(any(User.class));
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    @DisplayName("an existing account is never overwritten, so an administrator's own change survives a restart")
    void neverOverwritesAnExistingAccount() {
        enable();
        when(userRepository.existsByUsername(RAW_USERNAME)).thenReturn(true);

        bootstrapService.initialize();

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("falls back to the username when no display name is configured")
    void displayNameFallsBackToTheUsername() {
        enable();
        properties.setDisplayName(null);
        stubCreation();

        bootstrapService.initialize();

        assertThat(captureSaved().getDisplayName()).isEqualTo(RAW_USERNAME);
    }

    @Test
    @DisplayName("normalizes the configured username, so '  ROOT.ADMIN  ' is the same account as 'root.admin'")
    void normalizesTheUsername() {
        enable();
        properties.setUsername("  ROOT.ADMIN  ");
        stubCreation();

        bootstrapService.initialize();

        assertThat(captureSaved().getUsername()).isEqualTo(RAW_USERNAME);
    }

    @Test
    @DisplayName("does not trim the password, because leading and trailing spaces are real characters")
    void doesNotTrimThePassword() {
        enable();
        properties.setPassword("  PaddedPass1  ");
        stubCreation();

        bootstrapService.initialize();

        String stored = captureSaved().getPasswordHash();
        assertThat(passwordEncoder.matches("  PaddedPass1  ", stored)).isTrue();
        assertThat(passwordEncoder.matches("PaddedPass1", stored)).isFalse();
    }

    @Test
    @DisplayName("a configured password that breaks the policy is refused before anything is written")
    void refusesAWeakConfiguredPassword() {
        enable();
        properties.setPassword("weak");

        assertThatThrownBy(() -> bootstrapService.initialize())
                .isInstanceOf(InputValidationException.class);

        // The policy is checked before anything is encoded or written, so a bad configured
        // password can never end up as a usable account.
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("failing loudly when enabled but unconfigured beats starting up with no way in")
    void failsWhenEnabledWithoutAUsername() {
        enable();
        properties.setUsername(null);

        assertThatThrownBy(() -> bootstrapService.initialize())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("username")
                .hasMessageContaining("SMARTFIX_BOOTSTRAP_ADMIN_USERNAME");
    }

    @Test
    @DisplayName("a missing password fails startup and names the environment variable that supplies it")
    void failsWhenEnabledWithoutAPassword() {
        enable();
        properties.setPassword(null);

        assertThatThrownBy(() -> bootstrapService.initialize())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("password")
                .hasMessageContaining("SMARTFIX_BOOTSTRAP_ADMIN_PASSWORD");
    }

    @Test
    @DisplayName("the failure message names the property but never echoes a configured secret")
    void failureMessageNeverLeaksAConfiguredValue() {
        enable();
        properties.setUsername("   ");
        properties.setPassword("SuperSecretValue1");

        assertThatThrownBy(() -> bootstrapService.initialize())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("SuperSecretValue1");
    }

    @Test
    @DisplayName("the properties object never prints a configured secret either")
    void propertiesDoNotLeakSecretsWhenPrinted() {
        enable();
        properties.setDisplayName("Root Administrator");

        assertThat(properties.toString())
                .doesNotContain(RAW_PASSWORD)
                .doesNotContain(RAW_USERNAME);
    }

    // ---------------------------------------------------------------- helpers

    private void enable() {
        properties.setEnabled(true);
        properties.setUsername(RAW_USERNAME);
        properties.setPassword(RAW_PASSWORD);
    }

    private void stubCreation() {
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        });
    }

    private User captureSaved() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }
}
