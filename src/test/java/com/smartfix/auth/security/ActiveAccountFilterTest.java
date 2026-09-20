package com.smartfix.auth.security;

import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.UserAccessResponse;
import com.smartfix.user.dto.UserAuthenticationData;
import com.smartfix.user.service.UserService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActiveAccountFilterTest {
    private final UserService users = mock(UserService.class);
    private final ActiveAccountFilter filter = new ActiveAccountFilter(users);
    private final FilterChain chain = mock(FilterChain.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/home");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void anonymousRequestDoesNotLoadAnAccount() throws Exception {
        filter.doFilter(request, response, chain);
        verifyNoInteractions(users);
        verify(chain).doFilter(request, response);
    }

    @Test
    void currentAccountContinues() throws Exception {
        authenticate();
        when(users.getUserAccess(7L)).thenReturn(new UserAccessResponse(7L, Role.REQUESTER, AccountStatus.ACTIVE, 4L));
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    static Stream<UserAccessResponse> changedAccounts() {
        return Stream.of(new UserAccessResponse(7L, Role.REQUESTER, AccountStatus.DISABLED, 4L),
                new UserAccessResponse(7L, Role.REQUESTER, AccountStatus.ACTIVE, 5L),
                new UserAccessResponse(7L, Role.ADMINISTRATOR, AccountStatus.ACTIVE, 4L));
    }

    @ParameterizedTest
    @MethodSource("changedAccounts")
    void accountChangesInvalidateSessionAndPreventControllerAccess(UserAccessResponse access) throws Exception {
        MockHttpSession session = authenticate();
        when(users.getUserAccess(7L)).thenReturn(access);
        filter.doFilter(request, response, chain);
        assertExpired(session);
    }

    @Test
    void deletedAccountInvalidatesSession() throws Exception {
        MockHttpSession session = authenticate();
        when(users.getUserAccess(7L)).thenThrow(new ResourceNotFoundException("missing"));
        filter.doFilter(request, response, chain);
        assertExpired(session);
    }

    @Test
    void databaseFailureNeverLetsARequestThrough() {
        authenticate();
        when(users.getUserAccess(7L)).thenThrow(new DataAccessResourceFailureException("unavailable"));
        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verifyNoInteractions(chain);
    }

    private MockHttpSession authenticate() {
        SmartFixUserDetails principal = new SmartFixUserDetails(new UserAuthenticationData(
                7L, "alice", "hash", Role.REQUESTER, AccountStatus.ACTIVE, 4L));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        return session;
    }

    private void assertExpired(MockHttpSession session) {
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("/login?expired");
        assertThat(response.getCookie("JSESSIONID").getMaxAge()).isZero();
        verifyNoInteractions(chain);
    }
}
