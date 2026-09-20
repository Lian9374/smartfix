package com.smartfix.auth.service;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.UserAuthenticationData;
import com.smartfix.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmartFixUserDetailsServiceTest {
    private final UserService users = mock(UserService.class);
    private final SmartFixUserDetailsService service = new SmartFixUserDetailsService(users);

    @ParameterizedTest
    @EnumSource(Role.class)
    void mapsAccountIdentityAndAuthorities(Role role) {
        when(users.findAuthenticationByUsername("alice")).thenReturn(data(role, AccountStatus.ACTIVE));
        SmartFixUserDetails principal = service.loadUserByUsername("alice");
        assertThat(principal.getUserId()).isEqualTo(7L);
        assertThat(principal.getUsername()).isEqualTo("alice");
        assertThat(principal.getSecurityVersion()).isEqualTo(4L);
        assertThat(principal.getPassword()).isEqualTo("sensitive-hash");
        assertThat(principal.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_" + role.name());
        assertThat(principal.isEnabled()).isTrue();
    }

    @Test
    void disabledAccountCannotAuthenticate() {
        when(users.findAuthenticationByUsername("alice"))
                .thenReturn(data(Role.REQUESTER, AccountStatus.DISABLED));
        assertThat(service.loadUserByUsername("alice").isEnabled()).isFalse();
    }

    @Test
    void missingAccountBecomesGenericAuthenticationFailure() {
        when(users.findAuthenticationByUsername("missing"))
                .thenThrow(new ResourceNotFoundException("Internal account lookup details"));
        assertThatThrownBy(() -> service.loadUserByUsername("missing"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Invalid username or password.");
    }

    @Test
    void hashIsNeverLoggedOrSerializedAndCanBeErased() throws Exception {
        SmartFixUserDetails principal = new SmartFixUserDetails(data(Role.REQUESTER, AccountStatus.ACTIVE));
        assertThat(principal.toString()).doesNotContain("sensitive-hash");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(principal);
        }
        assertThat(bytes.toString(StandardCharsets.ISO_8859_1)).doesNotContain("sensitive-hash");
        principal.eraseCredentials();
        assertThat(principal.getPassword()).isNull();
    }

    private UserAuthenticationData data(Role role, AccountStatus status) {
        return new UserAuthenticationData(7L, "alice", "sensitive-hash", role, status, 4L);
    }
}
