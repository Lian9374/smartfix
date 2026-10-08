package com.smartfix.community.service;

import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.UserAccessResponse;
import com.smartfix.user.service.UserService;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * The one place the community module asks whether the acting account may act at all.
 *
 * <p>Every community service method starts here, including the read-only ones. That is
 * not redundancy with {@code ActiveAccountFilter}, which already refuses a request from a
 * disabled account: the filter guards the HTTP entry points, and the service methods are
 * also reachable from scheduled work, from another service in a later phase, and from
 * tests. Plan section 4.3.1 puts the authorisation decision on the server side for exactly
 * this reason, and the sprint 3 brief requires the service to re-check the ACTIVE status
 * rather than trust that a request got this far.</p>
 *
 * <p>A missing, disabled or otherwise unusable account answers {@link
 * ResourceNotFoundException} - a 404 - rather than 403. That matches {@code
 * RequestSubmissionService}, and it keeps the module's responses from confirming whether
 * an account exists to a caller who has no business asking.</p>
 */
@Component
public class CommunityAccessGuard {

    /** The message every refusal shares, so no refusal hints at what was actually wrong. */
    private static final String NOT_AVAILABLE = "Community content not found.";

    private final UserService users;

    public CommunityAccessGuard(UserService users) {
        this.users = users;
    }

    /**
     * Confirms the acting account exists and is still {@link AccountStatus#ACTIVE}.
     *
     * @param actorUserId the id taken from the authenticated principal, never from a form
     * @return that account's access context, for callers that also need its role
     * @throws ResourceNotFoundException when the account is absent or no longer usable
     */
    public UserAccessResponse requireActiveUser(Long actorUserId) {
        if (actorUserId == null) {
            throw new ResourceNotFoundException(NOT_AVAILABLE);
        }
        UserAccessResponse access = users.getUserAccess(actorUserId);
        if (access.accountStatus() != AccountStatus.ACTIVE) {
            throw new ResourceNotFoundException(NOT_AVAILABLE);
        }
        return access;
    }

    /**
     * Confirms the acting account is an active administrator.
     *
     * <h2>Why this is here and not only on the route</h2>
     *
     * <p>{@code SecurityConfig} already refuses {@code /admin/**} to anybody but an
     * administrator, and every moderation route lives under it. That is the outer
     * boundary, and it is not sufficient on its own: the service methods are also
     * reachable from tests, from a future scheduled job and from any other service in
     * the module, and a rule that lives only in a URL pattern is a rule that a new
     * caller can walk around without noticing. The sprint 3 brief requires exactly
     * this - moderation services check the role themselves, and the hidden controls on
     * the pages are courtesy rather than the rule.</p>
     *
     * <h2>Why a refusal is a 403 here and a 404 in {@link #requireActiveUser}</h2>
     *
     * <p>The two refusals answer different questions. "Not an administrator" is not a
     * secret worth keeping: the account exists, it is signed in, and it is being told
     * that this area is not for it - which is what {@code GlobalExceptionHandler} turns
     * into a 403 page. That differs from the community content rules, where 404 covers
     * the existence of <em>somebody else's content</em>. There is nothing to conceal
     * about the existence of a moderation area, and a signed-in requester who opens
     * {@code /admin/community/reports} by hand is better served by "you may not" than
     * by a page that pretends the route is not there.</p>
     *
     * <p>The account is checked for ACTIVE status first, so a disabled administrator
     * is refused as an unusable account rather than as an administrator - the same
     * ordering, and the same reason, as everywhere else in this module.</p>
     *
     * @param actorUserId the id taken from the authenticated principal, never from a form
     * @return that account's access context
     * @throws ResourceNotFoundException when the account is absent or no longer usable
     * @throws AccessDeniedException when the account is usable but is not an administrator
     */
    public UserAccessResponse requireAdministrator(Long actorUserId) {
        UserAccessResponse access = requireActiveUser(actorUserId);
        if (access.role() != Role.ADMINISTRATOR) {
            throw new AccessDeniedException("This area is for administrators.");
        }
        return access;
    }
}
