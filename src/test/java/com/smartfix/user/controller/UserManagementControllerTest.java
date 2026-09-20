package com.smartfix.user.controller;

import com.smartfix.common.exception.BusinessConflictException;
import com.smartfix.common.exception.InputValidationException;
import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.ChangeAccountStatusCommand;
import com.smartfix.user.dto.ChangeUserRoleCommand;
import com.smartfix.user.dto.CreateUserCommand;
import com.smartfix.user.dto.UserAuthenticationData;
import com.smartfix.user.dto.UserSummaryResponse;
import com.smartfix.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Tests for {@link UserManagementController}, including real Thymeleaf rendering.
 *
 * <p>Several of these assertions are about the rendered HTML rather than the model, which
 * is deliberate: a template that throws, or that echoes a submitted password back into
 * the page, is a defect that no amount of model inspection would catch.</p>
 *
 * <p><strong>Security filters are switched off.</strong> Who may reach
 * {@code /admin/**}, whether an anonymous visitor is redirected to a login page, and
 * whether a missing CSRF token is refused are covered by category B's security tests.
 * Turning the filters off here tests what this class actually owns - status
 * codes, model contents, redirects and rendering - and keeps these tests from breaking
 * the day category B replaces the temporary permit-all baseline. The consequence is that
 * these tests say nothing about access control; SecurityConfigTest and AuthenticationFlowIT
 * exercise the same routes with the real filters enabled.</p>
 */
@WebMvcTest(controllers = UserManagementController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserManagementControllerTest {

    private static final long USER_ID = 7L;
    private static final long ACTOR_ID = 99L;
    private static final String VALID_PASSWORD = "CorrectHorse1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    // ------------------------------------------------------------ listing

    @Test
    @DisplayName("GET /admin/users renders every account")
    void listsAccounts() throws Exception {
        when(userService.listUsers()).thenReturn(List.of(summary(USER_ID, "alice", Role.REQUESTER)));

        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users"))
                .andExpect(model().attribute("showCreateForm", false))
                .andExpect(model().attributeExists("users", "roles", "accountStatuses"))
                .andExpect(content().string(containsString("alice")));
    }

    @Test
    @DisplayName("GET /admin/users renders an empty list without failing")
    void listsNoAccounts() throws Exception {
        when(userService.listUsers()).thenReturn(List.of());

        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No accounts exist yet.")));
    }

    @Test
    @DisplayName("GET /admin/users/new renders the creation form")
    void showsTheCreationForm() throws Exception {
        when(userService.listUsers()).thenReturn(List.of());

        mockMvc.perform(get("/admin/users/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users"))
                .andExpect(model().attribute("showCreateForm", true))
                .andExpect(model().attributeExists("createUserCommand"));
    }

    @Test
    @DisplayName("the creation form always has a BindingResult, even before the first submit")
    void creationFormHasABindingResultOnFirstRender() throws Exception {
        // Without this, th:errors on the very first GET has nothing to read and the
        // template fails at render time rather than showing an empty form.
        when(userService.listUsers()).thenReturn(List.of());

        mockMvc.perform(get("/admin/users/new"))
                .andExpect(model().attributeExists(
                        "org.springframework.validation.BindingResult.createUserCommand"));
    }

    // ------------------------------------------------------------ creation

    @Test
    @DisplayName("a valid submission creates the account and redirects")
    void createsAnAccount() throws Exception {
        mockMvc.perform(validCreation())
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(userService).createUser(any(CreateUserCommand.class), isNull());
    }

    @Test
    @DisplayName("the actor is taken from the authenticated principal, never from the form")
    void takesTheActorFromThePrincipal() throws Exception {
        when(userService.findAuthenticationByUsername("root.admin")).thenReturn(
                new UserAuthenticationData(ACTOR_ID, "root.admin", "$2a$10$hash", Role.ADMINISTRATOR,
                        AccountStatus.ACTIVE, 0L));

        mockMvc.perform(validCreation().principal(() -> "root.admin"))
                .andExpect(status().is3xxRedirection());

        verify(userService).createUser(any(CreateUserCommand.class), eq(ACTOR_ID));
    }

    @Test
    @DisplayName("a form field the browser got wrong is a 400, and the service is never called")
    void rejectsAnInvalidSubmission() throws Exception {
        when(userService.listUsers()).thenReturn(List.of());

        mockMvc.perform(post("/admin/users")
                        .param("username", "")
                        .param("displayName", "")
                        .param("password", "")
                        .param("role", ""))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("admin/users"))
                .andExpect(model().attributeHasFieldErrors(
                        "createUserCommand", "username", "displayName", "password", "role"));

        verify(userService, never()).createUser(any(), any());
    }

    @Test
    @DisplayName("a password that breaks the policy is a 400 before the service is reached")
    void rejectsAWeakPassword() throws Exception {
        when(userService.listUsers()).thenReturn(List.of());

        mockMvc.perform(validCreation("short"))
                .andExpect(status().isBadRequest())
                .andExpect(model().attributeHasFieldErrors("createUserCommand", "password"));

        verify(userService, never()).createUser(any(), any());
    }

    @Test
    @DisplayName("a taken username is reported on the username field as a 409")
    void reportsADuplicateUsername() throws Exception {
        when(userService.createUser(any(CreateUserCommand.class), any()))
                .thenThrow(new BusinessConflictException("An account with this username already exists."));
        when(userService.listUsers()).thenReturn(List.of());

        mockMvc.perform(validCreation())
                .andExpect(status().isConflict())
                .andExpect(view().name("admin/users"))
                .andExpect(model().attributeHasFieldErrors("createUserCommand", "username"));
    }

    @Test
    @DisplayName("a rule only the service can check comes back as a 400 with the reason")
    void reportsAServiceSideValidationFailure() throws Exception {
        when(userService.createUser(any(CreateUserCommand.class), any()))
                .thenThrow(new InputValidationException("Username must be 3-50 characters."));
        when(userService.listUsers()).thenReturn(List.of());

        mockMvc.perform(validCreation())
                .andExpect(status().isBadRequest())
                .andExpect(model().attribute("formError", "Username must be 3-50 characters."));
    }

    @Test
    @DisplayName("a failed creation never echoes the submitted password back into the page")
    void neverEchoesThePassword() throws Exception {
        when(userService.createUser(any(CreateUserCommand.class), any()))
                .thenThrow(new InputValidationException("Refused."));
        when(userService.listUsers()).thenReturn(List.of());

        mockMvc.perform(validCreation().param("password", "TopSecretPass1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("TopSecretPass1"))));
    }

    // ------------------------------------------------------------ role change

    @Test
    @DisplayName("changing a role redirects and reports success")
    void changesARole() throws Exception {
        mockMvc.perform(post("/admin/users/{userId}/role", USER_ID).param("role", "TECHNICIAN"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(userService).changeRole(eq(USER_ID), any(ChangeUserRoleCommand.class), isNull());
    }

    @Test
    @DisplayName("submitting no role is a 400 and never reaches the service")
    void refusesARoleChangeWithoutASelection() throws Exception {
        when(userService.listUsers()).thenReturn(List.of());

        mockMvc.perform(post("/admin/users/{userId}/role", USER_ID).param("role", ""))
                .andExpect(status().isBadRequest())
                .andExpect(model().attributeExists("formError"));

        verify(userService, never()).changeRole(any(), any(), any());
    }

    @Test
    @DisplayName("demoting the last administrator comes back as a 409 with the reason on the page")
    void reportsARefusedRoleChange() throws Exception {
        doThrow(new BusinessConflictException("The last active administrator cannot be demoted."))
                .when(userService).changeRole(eq(USER_ID), any(ChangeUserRoleCommand.class), any());
        when(userService.listUsers()).thenReturn(List.of(summary(USER_ID, "root.admin", Role.ADMINISTRATOR)));

        mockMvc.perform(post("/admin/users/{userId}/role", USER_ID).param("role", "REQUESTER"))
                .andExpect(status().isConflict())
                .andExpect(view().name("admin/users"))
                .andExpect(model().attribute("formError",
                        "The last active administrator cannot be demoted."));
    }

    // ------------------------------------------------------------ status change

    @Test
    @DisplayName("changing a status redirects and reports success")
    void changesAStatus() throws Exception {
        mockMvc.perform(post("/admin/users/{userId}/status", USER_ID).param("accountStatus", "DISABLED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        verify(userService).changeAccountStatus(eq(USER_ID), any(ChangeAccountStatusCommand.class), isNull());
    }

    @Test
    @DisplayName("submitting no status is a 400 and never reaches the service")
    void refusesAStatusChangeWithoutASelection() throws Exception {
        when(userService.listUsers()).thenReturn(List.of());

        mockMvc.perform(post("/admin/users/{userId}/status", USER_ID).param("accountStatus", ""))
                .andExpect(status().isBadRequest())
                .andExpect(model().attributeExists("formError"));

        verify(userService, never()).changeAccountStatus(any(), any(), any());
    }

    @Test
    @DisplayName("disabling the last administrator comes back as a 409")
    void reportsARefusedStatusChange() throws Exception {
        doThrow(new BusinessConflictException("The last active administrator cannot be disabled."))
                .when(userService).changeAccountStatus(eq(USER_ID), any(ChangeAccountStatusCommand.class), any());
        when(userService.listUsers()).thenReturn(List.of(summary(USER_ID, "root.admin", Role.ADMINISTRATOR)));

        mockMvc.perform(post("/admin/users/{userId}/status", USER_ID).param("accountStatus", "DISABLED"))
                .andExpect(status().isConflict())
                .andExpect(model().attribute("formError",
                        "The last active administrator cannot be disabled."));
    }

    // ------------------------------------------------------------ unknown account

    @Test
    @DisplayName("an unknown account id renders category B's safe 404 page")
    void unknownAccountRendersNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Account 404 does not exist."))
                .when(userService).changeAccountStatus(eq(404L), any(ChangeAccountStatusCommand.class), any());

        mockMvc.perform(post("/admin/users/{userId}/status", 404L).param("accountStatus", "DISABLED"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error"))
                .andExpect(content().string(not(containsString("Account 404 does not exist."))));
    }

    // ------------------------------------------------------------ helpers

    private static MockHttpServletRequestBuilder validCreation() {
        return validCreation(VALID_PASSWORD);
    }

    private static MockHttpServletRequestBuilder validCreation(String password) {
        // A fresh builder per call: MockMvc's param() appends rather than replaces, so
        // overriding one value on an already-built request would submit both.
        return post("/admin/users")
                .param("username", "alice")
                .param("displayName", "Alice Tan")
                .param("password", password)
                .param("role", "REQUESTER");
    }

    private static UserSummaryResponse summary(long id, String username, Role role) {
        Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");
        return new UserSummaryResponse(id, username, "Display " + username, role,
                AccountStatus.ACTIVE, 0L, timestamp, timestamp);
    }
}
