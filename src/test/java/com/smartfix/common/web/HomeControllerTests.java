package com.smartfix.common.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.request.service.RequestQueryService;
import com.smartfix.user.domain.Role;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.dto.UserAccessResponse;
import com.smartfix.user.dto.UserAuthenticationData;
import com.smartfix.user.service.UserService;
import java.util.List;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * Verifies the authenticated home page through the real filter chain and template engine.
 *
 * <p>Both collaborators are mocked here: this test is about the controller's own
 * wiring - which attributes it sets, which view it returns, what the template
 * makes of them - not about the queries behind them. The real data path is
 * covered by {@code RequestPagesRenderingIT}, which runs the whole stack over an
 * H2 schema that actually holds requests.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HomeControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private RequestQueryService requestQueryService;

    @Test
    @DisplayName("GET / renders the SmartFix home page")
    void homePageRenders() throws Exception {
        when(userService.getUserAccess(7L)).thenReturn(
                new UserAccessResponse(7L, Role.REQUESTER, AccountStatus.ACTIVE, 0L));
        when(requestQueryService.listMyRequests(7L, 0, 5)).thenReturn(List.of());
        SmartFixUserDetails principal = new SmartFixUserDetails(new UserAuthenticationData(
                7L, "alice", "hash", Role.REQUESTER, AccountStatus.ACTIVE, 0L));
        mockMvc.perform(get("/").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(containsString("SmartFix")))
                .andExpect(content().string(containsString("Campus Facility Maintenance and Technician Dispatch System")))
                // The greeting is built from the account the shell already knows,
                // and the page names itself once rather than restating the product.
                .andExpect(content().string(containsString("Welcome back, alice.")))
                .andExpect(content().string(containsString("Recent requests")))
                // A requester's overview asks for their own five most recent
                // requests - the caller's own id, no wider.
                .andExpect(content().string(containsString("No requests yet")));
        // Plain values: every argument is a literal, so there is nothing for a
        // matcher to express that the value itself does not already say.
        verify(requestQueryService).listMyRequests(7L, 0, 5);
    }

    @Test
    @DisplayName("GET / does not query requests for a role that has none")
    void homePageDoesNotListRequestsForAnAdministrator() throws Exception {
        when(userService.getUserAccess(9L)).thenReturn(
                new UserAccessResponse(9L, Role.ADMINISTRATOR, AccountStatus.ACTIVE, 0L));
        SmartFixUserDetails principal = new SmartFixUserDetails(new UserAuthenticationData(
                9L, "root.admin", "hash", Role.ADMINISTRATOR, AccountStatus.ACTIVE, 0L));
        mockMvc.perform(get("/").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Administration")))
                .andExpect(content().string(not(containsString("Recent requests"))));
        verify(requestQueryService, never()).listMyRequests(any(), anyInt(), anyInt());
    }

    @Test
    void anonymousHomeRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("http://localhost/login"));
    }

    /**
     * The community board is the one destination every signed-in role shares, so the
     * home page offers it with no {@code navRole} condition and each role's navigation
     * carries the link.
     *
     * <p>Asserted for all three roles, not one, because that navigation is built by two
     * different fragments - the administrator's rail and everybody else's bar - and a
     * link added to only one of them would still pass a single-role check. The two
     * layouts spell their link text differently, which is why the expected string
     * depends on the role: the rail wraps it in a {@code <span>} beside its icon, the
     * bar writes it as plain text. Asserting one spelling for all three roles is exactly
     * how the rail would have been missed.</p>
     */
    @ParameterizedTest
    @EnumSource(Role.class)
    void everyRoleIsOfferedTheCommunityBoard(Role role) throws Exception {
        when(userService.getUserAccess(7L)).thenReturn(
                new UserAccessResponse(7L, role, AccountStatus.ACTIVE, 0L));
        SmartFixUserDetails principal = new SmartFixUserDetails(new UserAuthenticationData(
                7L, "alice", "hash", role, AccountStatus.ACTIVE, 0L));
        String navLink = role == Role.ADMINISTRATOR
                ? "<span>Community</span>"
                : ">Community</a>";
        mockMvc.perform(get("/").with(user(principal)))
                .andExpect(status().isOk())
                // The navigation link, in whichever of the two layouts this role uses.
                .andExpect(content().string(containsString(navLink)))
                // The card, and the three destinations that actually work today.
                .andExpect(content().string(containsString("id=\"community-tasks-title\"")))
                .andExpect(content().string(containsString("href=\"/community\"")))
                .andExpect(content().string(containsString("href=\"/community/questions/new\"")))
                .andExpect(content().string(containsString("href=\"/community/mine\"")));
    }
}
