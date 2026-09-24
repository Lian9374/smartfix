package com.smartfix.auth.config;

import com.smartfix.auth.controller.LoginController;
import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.auth.service.SmartFixUserDetailsService;
import com.smartfix.common.web.HomeController;
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

    static Stream<Arguments> routes() {
        List<Arguments> cases = new ArrayList<>();
        for (Role role : Role.values()) {
            for (String route : List.of("/requests/new", "/requests/mine")) {
                cases.add(Arguments.of(role, route, role == Role.REQUESTER ? 200 : 403));
            }
            for (String route : List.of("/requests/SF-2026-000001", "/requests/SF-2026-000001/attachments/1")) {
                cases.add(Arguments.of(role, route, role == Role.TECHNICIAN ? 403 : 200));
            }
            for (String route : List.of("/admin/users", "/admin/users/new", "/admin/requests/lookup")) {
                cases.add(Arguments.of(role, route, role == Role.ADMINISTRATOR ? 200 : 403));
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
    @ValueSource(strings = {"/", "/home", "/requests/new", "/requests/mine", "/admin/users",
            "/requests/SF-2026-000001", "/requests/SF-2026-000001/attachments/1"})
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
    }

    @Test
    void onlyRequesterCanSubmitEvenWithValidCsrf() throws Exception {
        mvc.perform(post("/requests").with(account(Role.REQUESTER)).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/requests").with(account(Role.ADMINISTRATOR)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/requests").with(account(Role.TECHNICIAN)).with(csrf())).andExpect(status().isForbidden());
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
        @GetMapping({"/requests/new", "/requests/mine", "/requests/{ticket}",
                "/requests/{ticket}/attachments/{id}", "/admin/requests/lookup"})
        String read() { return "authorized route probe"; }

        @PostMapping("/requests")
        String submit() { return "authorized route probe"; }
    }
}
