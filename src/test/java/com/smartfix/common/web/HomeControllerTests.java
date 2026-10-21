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
    @MockitoBean
    private com.smartfix.announcement.service.AnnouncementService announcements;

    @Test
    void homeLimitsNoticePreviewAndProvidesMultipleSlideControls() throws Exception {
        when(userService.getUserAccess(7L)).thenReturn(
                new UserAccessResponse(7L, Role.REQUESTER, AccountStatus.ACTIVE, 0L));
        when(requestQueryService.listMyRequests(7L, 0, 3)).thenReturn(List.of());
        var now = java.time.Instant.parse("2026-10-14T00:00:00Z");
        when(announcements.listVisible()).thenReturn(java.util.stream.LongStream.rangeClosed(1, 7)
                .mapToObj(id -> new com.smartfix.announcement.dto.AnnouncementResponse(id,
                        "Campus notice " + id, "Scheduled maintenance around campus. Please use the alternative entrance while work is underway.",
                        com.smartfix.announcement.domain.AnnouncementStatus.PUBLISHED,
                        now, null, now, now, null)).toList());
        var principal = new SmartFixUserDetails(new UserAuthenticationData(
                7L, "alice", "hash", Role.REQUESTER, AccountStatus.ACTIVE, 0L));
        var rendered = mockMvc.perform(get("/").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Announcement 6 of 6")))
                .andExpect(content().string(not(containsString("Campus notice 7"))))
                .andExpect(content().string(containsString("data-home-controls")))
                .andExpect(content().string(not(containsString("From report to repair"))))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        // Optional capture of the real rendered template for isolated visual verification.
        if (System.getProperty("smartfix.ui.captureDir") != null) {
            var capture = java.nio.file.Path.of(System.getProperty("smartfix.ui.captureDir"));
            java.nio.file.Files.createDirectories(capture);
            java.nio.file.Files.writeString(capture.resolve("home.html"), rendered);
        }
    }

    @Test
    void homeDisplaysEscapedVisibleAnnouncementAndFullDetailLink() throws Exception {
        when(userService.getUserAccess(7L)).thenReturn(
                new UserAccessResponse(7L, Role.REQUESTER, AccountStatus.ACTIVE, 0L));
        when(requestQueryService.listMyRequests(7L, 0, 3)).thenReturn(List.of());
        var now = java.time.Instant.parse("2026-10-14T00:00:00Z");
        when(announcements.listVisible()).thenReturn(List.of(
                new com.smartfix.announcement.dto.AnnouncementResponse(42L, "Library maintenance",
                        "<script>alert(1)</script>", com.smartfix.announcement.domain.AnnouncementStatus.PUBLISHED,
                        now, null, now, now, null)));
        var principal = new SmartFixUserDetails(new UserAuthenticationData(
                7L, "alice", "hash", Role.REQUESTER, AccountStatus.ACTIVE, 0L));
        var rendered = mockMvc.perform(get("/").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Library maintenance")))
                .andExpect(content().string(containsString("/announcements#announcement-42")))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(containsString("data-home-controls")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        capture("home-one.html", rendered);
        verify(announcements).listVisible();
    }

    @Test
    @DisplayName("GET / renders the SmartFix home page")
    void homePageRenders() throws Exception {
        when(userService.getUserAccess(7L)).thenReturn(
                new UserAccessResponse(7L, Role.REQUESTER, AccountStatus.ACTIVE, 0L));
        when(requestQueryService.listMyRequests(7L, 0, 3)).thenReturn(List.of());
        SmartFixUserDetails principal = new SmartFixUserDetails(new UserAuthenticationData(
                7L, "alice", "hash", Role.REQUESTER, AccountStatus.ACTIVE, 0L));
        var rendered = mockMvc.perform(get("/").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(containsString("SmartFix")))
                .andExpect(content().string(containsString("Campus Facility Maintenance and Technician Dispatch System")))
                // The greeting is built from the account the shell already knows,
                // and the page names itself once rather than restating the product.
                .andExpect(content().string(containsString("Welcome back, alice.")))
                .andExpect(content().string(containsString("Recent requests")))
                // A requester's overview asks for their own three most recent
                // requests - the caller's own id, no wider.
                .andExpect(content().string(containsString("No requests yet")))
                .andExpect(content().string(containsString("data-home-controls")))
                .andExpect(content().string(containsString("No current announcements.")))
                .andExpect(content().string(containsString("appbar--requester")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        capture("home-empty.html", rendered);
        // Plain values: every argument is a literal, so there is nothing for a
        // matcher to express that the value itself does not already say.
        verify(requestQueryService).listMyRequests(7L, 0, 3);
    }

    @Test
    void homeRendersRealRequestLinksStatusesAndSingaporeTimeWithoutInventedMetrics() throws Exception {
        when(userService.getUserAccess(7L)).thenReturn(
                new UserAccessResponse(7L, Role.REQUESTER, AccountStatus.ACTIVE, 0L));
        var created = java.time.Instant.parse("2026-10-15T01:00:00Z");
        var states = List.of(com.smartfix.request.domain.RequestStatus.SUBMITTED,
                com.smartfix.request.domain.RequestStatus.IN_PROGRESS,
                com.smartfix.request.domain.RequestStatus.RESOLVED,
                com.smartfix.request.domain.RequestStatus.CLOSED,
                com.smartfix.request.domain.RequestStatus.REOPENED);
        when(requestQueryService.listMyRequests(7L, 0, 3)).thenReturn(java.util.stream.IntStream.range(0, 3)
                .mapToObj(i -> new com.smartfix.request.dto.MaintenanceRequestSummaryResponse(
                        "SF-2026-00000" + (i + 1), i == 0 ? "Ceiling light flickering near the study room entrance on Level 2" : "Maintenance request " + (i + 1),
                        com.smartfix.request.domain.MaintenanceCategory.ELECTRICAL,
                        com.smartfix.request.domain.UrgencyLevel.MEDIUM, states.get(i),
                        "University Town · Education Resource Centre · Level 2", created)).toList());
        var principal = new SmartFixUserDetails(new UserAuthenticationData(
                7L, "alexandramontgomery", "hash", Role.REQUESTER, AccountStatus.ACTIVE, 0L));
        var rendered = mockMvc.perform(get("/").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/requests/SF-2026-000001")))
                .andExpect(content().string(containsString("15 Oct · 09:00")))
                .andExpect(content().string(containsString("University Town")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        verify(requestQueryService).listMyRequests(7L, 0, 3);
        capture("home-populated.html", rendered);
    }

    private void capture(String filename, String rendered) throws java.io.IOException {
        if (System.getProperty("smartfix.ui.captureDir") == null) return;
        var directory = java.nio.file.Path.of(System.getProperty("smartfix.ui.captureDir"));
        java.nio.file.Files.createDirectories(directory);
        java.nio.file.Files.writeString(directory.resolve(filename), rendered);
    }

    @Test
    @DisplayName("GET / does not query requests for a role that has none")
    void homePageDoesNotListRequestsForAnAdministrator() throws Exception {
        when(userService.getUserAccess(9L)).thenReturn(
                new UserAccessResponse(9L, Role.ADMINISTRATOR, AccountStatus.ACTIVE, 0L));
        SmartFixUserDetails principal = new SmartFixUserDetails(new UserAuthenticationData(
                9L, "root.admin", "hash", Role.ADMINISTRATOR, AccountStatus.ACTIVE, 0L));
        mockMvc.perform(get("/").with(user(principal)))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/admin"));
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
        if (role != Role.REQUESTER) {
            mockMvc.perform(get("/").with(user(principal)))
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl(role == Role.ADMINISTRATOR ? "/admin" : "/technician"));
            return;
        }
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
