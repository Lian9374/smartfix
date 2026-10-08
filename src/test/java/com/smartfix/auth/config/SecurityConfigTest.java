package com.smartfix.auth.config;

import com.smartfix.auth.controller.LoginController;
import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.auth.service.SmartFixUserDetailsService;
import com.smartfix.common.web.HomeController;
import com.smartfix.request.service.RequestQueryService;
import com.smartfix.user.config.PasswordConfig;
import com.smartfix.user.controller.UserManagementController;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.UserAccessResponse;
import com.smartfix.user.dto.UserAuthenticationData;
import com.smartfix.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {LoginController.class, HomeController.class, UserManagementController.class,
        SecurityConfigTest.RequestRouteProbes.class})
@Import({SecurityConfig.class, SmartFixUserDetailsService.class, PasswordConfig.class,
        SecurityConfigTest.RequestRouteProbes.class})
class SecurityConfigTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private UserService users;
    // HomeController reads a requester's recent requests to render the overview.
    // This slice deliberately loads controllers without the service layer, so the
    // collaborator is mocked like every other one here; the route matrix this test
    // exists for is unaffected by what the overview chooses to list.
    @MockitoBean private RequestQueryService requestQueryService;

    private static final List<String> AUTHENTICATED_READS = List.of(
            "/community", "/community/mine", "/community/questions/new", "/community/questions/1",
            "/community/questions/1/edit", "/community/answers/1/edit", "/notifications", "/announcements");
    private static final List<String> AUTHENTICATED_WRITES = List.of(
            "/community/questions", "/community/questions/1", "/community/questions/1/withdraw",
            "/community/questions/1/answers", "/community/answers/1", "/community/answers/1/withdraw",
            "/community/questions/1/answers/1/accept", "/community/questions/1/acceptance/remove",
            "/community/questions/1/reports", "/community/answers/1/reports", "/notifications/1/read");
    private static final List<String> ADMINISTRATOR_READS = List.of(
            "/admin/community/reports", "/admin/sla/policies", "/admin/reports", "/admin/reports/export.csv",
            "/admin/announcements", "/admin/audit");
    private static final List<String> ADMINISTRATOR_WRITES = List.of(
            "/admin/community/reports/1/resolve", "/admin/community/questions/1/hide", "/admin/community/questions/1/restore",
            "/admin/community/answers/1/hide", "/admin/community/answers/1/restore", "/admin/sla/policies",
            "/admin/facilities/1/status", "/admin/announcements");

    static Stream<Arguments> routes() {
        List<Arguments> cases = new ArrayList<>();
        for (Role role : Role.values()) {
            cases.add(Arguments.of(role, "/technician/profile", role == Role.TECHNICIAN ? 200 : 403));
            cases.add(Arguments.of(role, "/campus-map", 200));
            for (String route : List.of("/requests/new", "/requests/mine")) {
                cases.add(Arguments.of(role, route, role == Role.REQUESTER ? 200 : 403));
            }
            for (String route : List.of("/requests/SF-2026-000001", "/requests/SF-2026-000001/attachments/1")) {
                cases.add(Arguments.of(role, route, 200));
            }
            for (String route : List.of("/requests/SF-2026-000001/review", "/admin/requests", "/admin/requests/SF-2026-000001/dispatch")) {
                cases.add(Arguments.of(role, route, role == Role.ADMINISTRATOR ? 200 : 403));
            }
            for (String route : List.of("/workorders/mine", "/workorders/1")) {
                cases.add(Arguments.of(role, route, role == Role.TECHNICIAN ? 200 : 403));
            }
            for (String route : List.of(
                "/admin/users",
                "/admin/users/new",
                "/admin/requests/lookup",
                "/admin/facilities")) {
                cases.add(Arguments.of(
                    role,
                    route,
                    role == Role.ADMINISTRATOR ? 200 : 403));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("routes")
    void enforcesTheRouteMatrix(Role role, String route, int expected) throws Exception {
        mvc.perform(get(route).with(account(role))).andExpect(status().is(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/home", "/campus-map", "/requests/new", "/requests/mine", "/admin/users",
        "/requests/SF-2026-000001", "/requests/SF-2026-000001/attachments/1", "/technician/profile",
        "/admin/requests", "/admin/requests/SF-2026-000001/dispatch"})
    void anonymousPageAccessRedirectsToLogin(String route) throws Exception {
        mvc.perform(get(route)).andExpect(status().isFound()).andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    void loginPageHasCsrfAndOnlyGenericFailureText() throws Exception {
        mvc.perform(get("/login?error"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("Invalid username or password.")));
        mvc.perform(get("/css/site.css")).andExpect(status().isOk());
    }

    @Test
    void loginAndLogoutAndWritesRequireCsrf() throws Exception {
        mvc.perform(post("/login")).andExpect(status().isForbidden());
        mvc.perform(post("/logout").with(account(Role.REQUESTER))).andExpect(status().isForbidden());
        mvc.perform(post("/requests").with(account(Role.REQUESTER))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/users").with(account(Role.ADMINISTRATOR))).andExpect(status().isForbidden());
        mvc.perform(post("/technician/profile").with(account(Role.TECHNICIAN))).andExpect(status().isForbidden());
    }

    @Test
    void onlyRequesterCanSubmitEvenWithValidCsrf() throws Exception {
        mvc.perform(post("/requests").with(account(Role.REQUESTER)).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/requests").with(account(Role.ADMINISTRATOR)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/requests").with(account(Role.TECHNICIAN)).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void sprint3WritesEnforceRolesAndCsrf() throws Exception {
        for (Role role : Role.values()) {
            for (String action : List.of("confirm", "feedback", "reopen", "cancel", "review", "close")) {
                String route = "/requests/SF-2026-000001/" + action;
                boolean administratorAction = action.equals("review") || action.equals("close");
                boolean permitted = administratorAction ? role == Role.ADMINISTRATOR : role == Role.REQUESTER;
                mvc.perform(post(route).with(account(role)).with(csrf())).andExpect(status().is(permitted ? 200 : 403));
                mvc.perform(post(route).with(account(role))).andExpect(status().isForbidden());
            }
            for (String action : List.of("accept", "records", "complete")) {
                String route = "/workorders/1/" + action;
                mvc.perform(post(route).with(account(role)).with(csrf())).andExpect(status().is(role == Role.TECHNICIAN ? 200 : 403));
                mvc.perform(post(route).with(account(role))).andExpect(status().isForbidden());
            }
            for (String action : List.of("assign", "reassign", "withdraw")) {
                String route = "/admin/requests/SF-2026-000001/" + action;
                mvc.perform(post(route).with(account(role)).with(csrf()))
                        .andExpect(status().is(role == Role.ADMINISTRATOR ? 200 : 403));
                mvc.perform(post(route).with(account(role))).andExpect(status().isForbidden());
                mvc.perform(post(route).with(account(role)).with(csrf().useInvalidToken())).andExpect(status().isForbidden());
                mvc.perform(post(route).with(csrf())).andExpect(status().isFound());
            }
        }
    }

    @Test
    void frozenSprint3ContractsEnforceEveryRoleAndAnonymousAccess() throws Exception {
        for (Role role : Role.values()) {
            for (String route : AUTHENTICATED_READS) {
                mvc.perform(get(route).with(account(role))).andExpect(status().isOk());
            }
            for (String route : ADMINISTRATOR_READS) {
                mvc.perform(get(route).with(account(role))).andExpect(status().is(role == Role.ADMINISTRATOR ? 200 : 403));
            }
            mvc.perform(get("/dashboard").with(account(role))).andExpect(status().is(role == Role.REQUESTER ? 403 : 200));
            for (String route : AUTHENTICATED_WRITES) {
                mvc.perform(post(route).with(account(role)).with(csrf())).andExpect(status().isOk());
                mvc.perform(post(route).with(account(role))).andExpect(status().isForbidden());
            }
            for (String route : ADMINISTRATOR_WRITES) {
                mvc.perform(post(route).with(account(role)).with(csrf())).andExpect(status().is(role == Role.ADMINISTRATOR ? 200 : 403));
                mvc.perform(post(route).with(account(role))).andExpect(status().isForbidden());
            }
            mvc.perform(post("/technician/profile").with(account(role)).with(csrf()))
                    .andExpect(status().is(role == Role.TECHNICIAN ? 200 : 403));
        }
        for (String route : Stream.concat(AUTHENTICATED_READS.stream(), ADMINISTRATOR_READS.stream()).toList()) {
            mvc.perform(get(route)).andExpect(status().isFound()).andExpect(redirectedUrl("http://localhost/login"));
        }
        mvc.perform(get("/dashboard")).andExpect(status().isFound());
        for (String route : Stream.concat(AUTHENTICATED_WRITES.stream(), ADMINISTRATOR_WRITES.stream()).toList()) {
            mvc.perform(post(route).with(csrf())).andExpect(status().isFound());
            mvc.perform(post(route)).andExpect(status().isForbidden());
        }
    }

    @Test
    void communityNewRoutePrecedesTheQuestionIdRoute() throws Exception {
        mvc.perform(get("/community/questions/new").with(account(Role.REQUESTER)))
                .andExpect(status().isOk()).andExpect(content().string("question form route probe"));
        mvc.perform(get("/community/questions/1").with(account(Role.REQUESTER)))
                .andExpect(status().isOk()).andExpect(content().string("authorized route probe"));
    }

    @Test
    void unlistedRoutesAndUnsafeMethodsStayClosed() throws Exception {
        mvc.perform(get("/actuator/env").with(account(Role.ADMINISTRATOR))).andExpect(status().isForbidden());
        mvc.perform(delete("/requests/anything").with(account(Role.ADMINISTRATOR)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/logout").with(account(Role.REQUESTER))).andExpect(status().isForbidden());
        mvc.perform(delete("/technician/profile").with(account(Role.TECHNICIAN)).with(csrf()))
                .andExpect(status().isForbidden());
        for (String action : List.of("assign", "reassign", "withdraw")) {
            mvc.perform(get("/admin/requests/SF-2026-000001/" + action).with(account(Role.ADMINISTRATOR)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/admin/requests/SF-2026-000001/dispatch").with(account(Role.ADMINISTRATOR)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/admin/requests/SF-2026-000001/reassign").with(account(Role.ADMINISTRATOR)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/dashboard").with(account(Role.ADMINISTRATOR)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/notifications/1/read").with(account(Role.REQUESTER))).andExpect(status().isForbidden());
        mvc.perform(delete("/community/questions/1").with(account(Role.ADMINISTRATOR)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/community/unknown").with(account(Role.REQUESTER))).andExpect(status().isForbidden());
    }

    private RequestPostProcessor account(Role role) {
        when(users.getUserAccess(7L)).thenReturn(new UserAccessResponse(7L, role, AccountStatus.ACTIVE, 0L));
        return user(new SmartFixUserDetails(new UserAuthenticationData(
                7L, "alice", "hash", role, AccountStatus.ACTIVE, 0L)));
    }

    /** Route-only probes for C/D/E's contracts; these do not claim their business logic exists. */
    @RestController
    static class RequestRouteProbes {
        @GetMapping({
            "/requests/new",
            "/requests/mine",
            "/requests/{ticket}",
            "/requests/{ticket}/attachments/{id}",
            "/admin/requests/lookup",
            "/admin/requests",
            "/admin/requests/{ticket}/dispatch",
            "/requests/{ticket}/review",
            "/workorders/mine",
            "/workorders/{id}",
            "/campus-map",
            "/admin/facilities",
            "/community", "/community/mine", "/community/questions/{id}", "/community/questions/{id}/edit",
            "/community/answers/{id}/edit", "/notifications", "/announcements", "/dashboard",
            "/admin/community/reports", "/admin/sla/policies", "/admin/reports", "/admin/reports/export.csv",
            "/admin/announcements", "/admin/audit",
            "/technician/profile"
        })
        String read() {
            return "authorized route probe";
        }

        @GetMapping("/community/questions/new")
        String questionForm() { return "question form route probe"; }

        @PostMapping({"/requests", "/requests/{ticket}/confirm", "/requests/{ticket}/feedback",
                "/requests/{ticket}/reopen", "/requests/{ticket}/cancel", "/requests/{ticket}/review",
                "/requests/{ticket}/close", "/workorders/{id}/accept", "/workorders/{id}/records", "/workorders/{id}/complete",
                "/admin/requests/{ticket}/assign", "/admin/requests/{ticket}/reassign", "/admin/requests/{ticket}/withdraw",
                "/technician/profile", "/community/questions", "/community/questions/{id}",
                "/community/questions/{id}/withdraw", "/community/questions/{id}/answers",
                "/community/answers/{id}", "/community/answers/{id}/withdraw",
                "/community/questions/{id}/answers/{answerId}/accept", "/community/questions/{id}/acceptance/remove",
                "/community/questions/{id}/reports", "/community/answers/{id}/reports", "/notifications/{id}/read",
                "/admin/community/reports/{id}/resolve", "/admin/community/questions/{id}/hide", "/admin/community/questions/{id}/restore",
                "/admin/community/answers/{id}/hide", "/admin/community/answers/{id}/restore", "/admin/sla/policies",
                "/admin/facilities/{id}/status", "/admin/announcements"})
        String submit() { return "authorized route probe"; }
    }
}
