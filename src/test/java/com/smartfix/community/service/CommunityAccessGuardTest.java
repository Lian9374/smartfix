package com.smartfix.community.service;

import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.UserAccessResponse;
import com.smartfix.user.service.UserService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The ACTIVE re-check every community service method begins with.
 *
 * <p>Small, and tested on its own because of what it is: the brief requires the service
 * layer to confirm the acting account is still active rather than assume that reaching the
 * service means the request was allowed. If this returned without checking, every service
 * test in this module would still pass while the rule was gone.</p>
 */
class CommunityAccessGuardTest {

    private static final Long ACTOR = 7L;

    private UserService users;
    private CommunityAccessGuard guard;

    @BeforeEach
    void setUp() {
        users = mock(UserService.class);
        guard = new CommunityAccessGuard(users);
    }

    @Test
    void anActiveAccountIsAllowedThrough() {
        UserAccessResponse access = access(AccountStatus.ACTIVE);
        when(users.getUserAccess(ACTOR)).thenReturn(access);

        assertThat(guard.requireActiveUser(ACTOR)).isSameAs(access);
    }

    @Test
    void aDisabledAccountIsNotFound() {
        when(users.getUserAccess(ACTOR)).thenReturn(access(AccountStatus.DISABLED));

        assertThatThrownBy(() -> guard.requireActiveUser(ACTOR))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aMissingPrincipalIsNotFoundWithoutAskingTheUserModule() {
        assertThatThrownBy(() -> guard.requireActiveUser(null))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(users);
    }

    @Test
    void anAccountThatDoesNotExistIsNotFound() {
        when(users.getUserAccess(anyLong()))
                .thenThrow(new ResourceNotFoundException("Account not found."));

        assertThatThrownBy(() -> guard.requireActiveUser(ACTOR))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /**
     * A refusal must not say which of the several reasons applied. A caller who is told
     * "that account is disabled" has learned something about an account that is not
     * theirs, so every refusal carries the same wording.
     */
    @Test
    void everyRefusalCarriesTheSameMessage() {
        when(users.getUserAccess(ACTOR)).thenReturn(access(AccountStatus.DISABLED));

        String disabled = messageOf(ACTOR);
        String missing = messageOf(null);

        assertThat(disabled).isEqualTo(missing);
    }

    private String messageOf(Long actorUserId) {
        try {
            guard.requireActiveUser(actorUserId);
            throw new AssertionError("expected a refusal");
        } catch (ResourceNotFoundException expected) {
            return expected.getMessage();
        }
    }

    private UserAccessResponse access(AccountStatus status) {
        return new UserAccessResponse(ACTOR, Role.REQUESTER, status, 0L);
    }
}
