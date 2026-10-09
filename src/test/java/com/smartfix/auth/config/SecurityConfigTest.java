package com.smartfix.auth.config;

import com.smartfix.auth.controller.LoginController;
import com.smartfix.auth.controller.RegistrationController;
import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.auth.service.SmartFixUserDetailsService;
import com.smartfix.common.web.HomeController;
import com.smartfix.request.service.RequestAssignmentAccessService;
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

@WebMvcTest(controllers = {LoginController.class, RegistrationController.class, HomeController.class,
        UserManagementController.class, SecurityConfigTest.RequestRouteProbes.class})
@Import({SecurityConfig.class, SmartFixUserDetailsService.class, PasswordConfig.class,
        SecurityConfigTest.RequestRouteProbes.class})
class SecurityConfigTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private UserService users;
    @MockitoBean private com.smartfix.auth.service.RegistrationRateLimiter registrationLimiter;
    // HomeController reads a requester's recent requests to render the overview.
    // This slice deliberately loads controllers without the service layer, so the
    // collaborator is mocked like every other one here; the route matrix this test
    // exists for is unaffected by what the overview chooses to list.
    @MockitoBean private RequestQueryService requestQueryService;
    // And its second one: the overview asks dispatch whether assigning is possible at all,
    // to decide what the technician's card says. Same reasoning as above.
    @MockitoBean private RequestAssignmentAccessService requestAssignmentAccessService;

    static Stream<Arguments> routes() {
        List<Arguments> cases = new ArrayList<>();
        for (Role role : Role.values()) {
            cases.add(Arguments.of(role, "/campus-map", 200));

            for (String route : List.of("/requests/new", "/requests/mine")) {
                cases.add(Arguments.of(role, route, role == Role.REQUESTER ? 200 : 403));
            }
            for (String route : List.of("/requests/SF-2026-000001", "/requests/SF-2026-000001/attachments/1")) {
                cases.add(Arguments.of(role, route, 200));
            }
            for (String route : List.of("/requests/SF-2026-000001/review")) {
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
        "/requests/SF-2026-000001", "/requests/SF-2026-000001/attachments/1", "/community"})
    void anonymousPageAccessRedirectsToLogin(String route) throws Exception {
        mvc.perform(get(route)).andExpect(status().isFound()).andExpect(redirectedUrl("http://localhost/login"));
    }

    /**
     * The community board is one shared place, so its write routes carry no role condition:
     * every signed-in role may post an answer, edit one, withdraw one, accept one or take an
     * acceptance back. What decides who may actually do it is ownership, which is checked in
     * the service and is not something a URL pattern can express.
     *
     * <p>CSRF is checked here as well, because "open to every role" must not be read as
     * "open": a write still needs the token every other write needs.</p>
     */
    @Test
    void communityWritesAreForEverySignedInRoleAndStillRequireCsrf() throws Exception {
        for (Role role : Role.values()) {
            for (String route : List.of(
                    "/community/questions/1/answers",
                    "/community/answers/1",
                    "/community/answers/1/withdraw",
                    "/community/questions/1/answers/1/accept",
                    "/community/questions/1/acceptance/remove")) {
                mvc.perform(post(route).with(account(role)).with(csrf()))
                        .andExpect(status().isOk());
                mvc.perform(post(route).with(account(role)))
                        .andExpect(status().isForbidden());
            }
        }
    }

    @Test
    void loginPageHasCsrfAndOnlyGenericFailureText() throws Exception {
        mvc.perform(get("/login?error"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("Invalid username or password.")));
        mvc.perform(get("/css/site.css")).andExpect(status().isOk());
    }

    /**
     * The sign-up route is the only page open before anybody has signed in, and the only
     * anonymous write in the matrix. Permission to fetch the form and permission to submit
     * it are asserted separately, because a route that is {@code permitAll} for both methods
     * would still be a route whose POST is CSRF-protected, and only the second assertion
     * can tell those apart.
     */
    @Test
    void theSignUpPageIsAnonymousAndItsSubmissionStillNeedsCsrf() throws Exception {
        mvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(post("/register")).andExpect(status().isForbidden());
    }

    /**
     * Opening {@code /register} opens exactly that path.
     *
     * <p>Named-route matching rather than a prefix, so nothing that merely starts with the
     * same word comes with it, and the account module stays closed to a visitor who has not
     * signed in.</p>
     */
    @Test
    void nothingIsOpenedAlongWithTheSignUpRoute() throws Exception {
        for (String neighbour : List.of("/register/", "/register/anything", "/admin/users", "/admin/users/new")) {
            mvc.perform(get(neighbour)).andExpect(status().isFound())
                    .andExpect(redirectedUrl("http://localhost/login"));
        }
    }

    @Test
    void loginAndLogoutAndWritesRequireCsrf() throws Exception {
        mvc.perform(post("/login")).andExpect(status().isForbidden());
        mvc.perform(post("/logout").with(account(Role.REQUESTER))).andExpect(status().isForbidden());
        mvc.perform(post("/requests").with(account(Role.REQUESTER))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/users").with(account(Role.ADMINISTRATOR))).andExpect(status().isForbidden());
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
        }
    }

    @Test
    void unlistedRoutesAndUnsafeMethodsStayClosed() throws Exception {
        mvc.perform(get("/actuator/env").with(account(Role.ADMINISTRATOR))).andExpect(status().isForbidden());
        mvc.perform(delete("/requests/anything").with(account(Role.ADMINISTRATOR)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/logout").with(account(Role.REQUESTER))).andExpect(status().isForbidden());
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
            "/requests/{ticket}/review",
            "/workorders/mine",
            "/workorders/{id}",
            "/campus-map",
            "/admin/facilities"
        })
        String read() {
            return "authorized route probe";
        }

        @PostMapping({"/requests", "/requests/{ticket}/confirm", "/requests/{ticket}/feedback",
                "/requests/{ticket}/reopen", "/requests/{ticket}/cancel", "/requests/{ticket}/review",
                "/requests/{ticket}/close", "/workorders/{id}/accept", "/workorders/{id}/records", "/workorders/{id}/complete"})
        String submit() { return "authorized route probe"; }

        /*
         * The answer routes. Only their shape and their authorisation are claimed here; the
         * real controller is not loaded by this slice, and the ownership rules behind them
         * are exercised end to end in CommunityPagesIT and directly in
         * CommunityAnswerServiceTest.
         */
        @PostMapping({"/community/questions/{id}/answers",
                "/community/answers/{id}",
                "/community/answers/{id}/withdraw",
                "/community/questions/{id}/answers/{answerId}/accept",
                "/community/questions/{id}/acceptance/remove"})
        String communityWrite() { return "authorized route probe"; }
    }
}
