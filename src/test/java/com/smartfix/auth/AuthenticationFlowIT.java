package com.smartfix.auth;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.ChangeAccountStatusCommand;
import com.smartfix.user.dto.CreateUserCommand;
import com.smartfix.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real A services, BCrypt, persistence, B filters and templates; only the database is H2. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:smartfix-auth-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "smartfix.bootstrap-admin.enabled=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Sql("/db/auth-test-schema.sql")
class AuthenticationFlowIT {
    // Synthetic test-only credential, never an application default.
    private static final String PASSWORD = "TestPassword9";
    @Autowired private MockMvc mvc;
    @Autowired private UserService users;
    @LocalServerPort private int port;
    private Long requesterId;

    @BeforeEach
    void accounts() {
        create("root.admin", Role.ADMINISTRATOR);
        requesterId = create("alice", Role.REQUESTER);
        create("tech", Role.TECHNICIAN);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void allRolesCanLoginAndGetTheirOwnHome(Role role) throws Exception {
        String username = switch (role) {
            case REQUESTER -> "alice";
            case TECHNICIAN -> "tech";
            case ADMINISTRATOR -> "root.admin";
        };
        MockHttpSession session = login(username);
        var result = mvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(role.name())))
                .andExpect(content().string(containsString("Sign out")));
        if (role == Role.TECHNICIAN) {
            result.andExpect(content().string(containsString("Technician workspace")))
                    .andExpect(content().string(not(containsString("href=\"/requests/new\""))));
        }
        if (role == Role.REQUESTER) {
            result.andExpect(content().string(not(containsString("href=\"/admin/users\""))));
        }
    }

    @Test
    void normalizesUsernameAndErasesCredentialsFromSession() throws Exception {
        MockHttpSession session = login(" ALICE ");
        SecurityContext context = (SecurityContext) session.getAttribute(SPRING_SECURITY_CONTEXT_KEY);
        SmartFixUserDetails principal = (SmartFixUserDetails) context.getAuthentication().getPrincipal();
        assertThat(principal.getUserId()).isEqualTo(requesterId);
        assertThat(principal.getUsername()).isEqualTo("alice");
        assertThat(principal.getPassword()).isNull();
        assertThat(context.getAuthentication().getCredentials()).isNull();
    }

    @Test
    void unknownWrongAndDisabledCredentialsHaveSamePublicResult() throws Exception {
        for (String username : new String[]{"missing", "alice"}) {
            mvc.perform(post("/login").with(csrf()).param("username", username).param("password", "WrongPassword9"))
                    .andExpect(status().isFound()).andExpect(redirectedUrl("/login?error"));
        }
        ChangeAccountStatusCommand change = new ChangeAccountStatusCommand();
        change.setAccountStatus(AccountStatus.DISABLED);
        users.changeAccountStatus(requesterId, change, null);
        mvc.perform(post("/login").with(csrf()).param("username", "alice").param("password", PASSWORD))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/login?error"));
        mvc.perform(get("/login?error"))
                .andExpect(content().string(containsString("Invalid username or password.")))
                .andExpect(content().string(not(containsString("disabled"))));
    }

    @Test
    void loginChangesExistingSessionId() throws Exception {
        MockHttpSession before = new MockHttpSession();
        String originalId = before.getId();
        MockHttpSession after = (MockHttpSession) mvc.perform(post("/login").session(before).with(csrf())
                .param("username", "alice").param("password", PASSWORD))
                .andExpect(authenticated()).andReturn().getRequest().getSession(false);
        assertThat(after).isNotNull();
        assertThat(after.getId()).isNotEqualTo(originalId);
    }

    @Test
    void administratorCanCreateAccountThroughCsrfProtectedForm() throws Exception {
        MockHttpSession admin = login("root.admin");
        mvc.perform(get("/admin/users/new").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(not(containsString("$2"))));
        mvc.perform(post("/admin/users").session(admin).with(csrf())
                        .param("username", "new.user").param("displayName", "New User")
                        .param("password", PASSWORD).param("role", "REQUESTER"))
                .andExpect(redirectedUrl("/admin/users"));
        assertThat(users.findAuthenticationByUsername("new.user").passwordHash()).startsWith("$2");
        mvc.perform(get("/home").session(login("new.user"))).andExpect(status().isOk());
    }

    @Test
    void disabledAccountLosesAllExistingSessionsOnTheirNextRequests() throws Exception {
        MockHttpSession first = login("alice");
        MockHttpSession second = login("alice");
        MockHttpSession admin = login("root.admin");
        mvc.perform(post("/admin/users/{id}/status", requesterId).session(admin).with(csrf())
                        .param("accountStatus", "DISABLED"))
                .andExpect(redirectedUrl("/admin/users"));
        for (MockHttpSession session : new MockHttpSession[]{first, second}) {
            mvc.perform(get("/home").session(session)).andExpect(redirectedUrl("/login?expired"));
            assertThat(session.isInvalid()).isTrue();
        }
    }

    @Test
    void roleChangeInvalidatesOldSessionAndNewLoginGetsNewRole() throws Exception {
        MockHttpSession old = login("alice");
        MockHttpSession admin = login("root.admin");
        mvc.perform(post("/admin/users/{id}/role", requesterId).session(admin).with(csrf())
                        .param("role", "TECHNICIAN"))
                .andExpect(redirectedUrl("/admin/users"));
        mvc.perform(get("/home").session(old)).andExpect(redirectedUrl("/login?expired"));
        MockHttpSession current = login("alice");
        mvc.perform(get("/home").session(current))
                .andExpect(content().string(containsString("Technician workspace")));
        mvc.perform(get("/requests/new").session(current)).andExpect(status().isForbidden());
    }

    @Test
    void missingCsrfDoesNotChangeDatabase() throws Exception {
        MockHttpSession admin = login("root.admin");
        mvc.perform(post("/admin/users/{id}/status", requesterId).session(admin)
                        .param("accountStatus", "DISABLED"))
                .andExpect(status().isForbidden());
        assertThat(users.getUserAccess(requesterId).accountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void requesterCannotCreateUsersEvenWithValidCsrf() throws Exception {
        mvc.perform(post("/admin/users").session(login("alice")).with(csrf())
                        .param("username", "intruder").param("displayName", "Intruder")
                        .param("password", PASSWORD).param("role", "ADMINISTRATOR"))
                .andExpect(status().isForbidden());
        assertThat(users.listUsers()).hasSize(3);
    }

    @Test
    void logoutClearsSessionAndProtectedPagesRedirect() throws Exception {
        MockHttpSession session = login("alice");
        mvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(redirectedUrl("/login?logout"))
                .andExpect(cookie().maxAge("JSESSIONID", 0));
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/home")).andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    void missingAccountGetsSafe404WithFiltersEnabled() throws Exception {
        mvc.perform(post("/admin/users/999999/status").session(login("root.admin")).with(csrf())
                        .param("accountStatus", "DISABLED"))
                .andExpect(status().isNotFound()).andExpect(view().name("error"))
                .andExpect(content().string(not(containsString("ResourceNotFoundException"))));
    }

    @Test
    void realHttpLoginUsesRenderedCsrfAndContainerRendersForbiddenPage() throws Exception {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        try (HttpClient browser = HttpClient.newBuilder().cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER).build()) {
            URI base = URI.create("http://localhost:" + port);
            var page = browser.send(HttpRequest.newBuilder(base.resolve("/login")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            var matcher = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.body());
            assertThat(matcher.find()).isTrue();
            String form = "username=alice&password=" + PASSWORD + "&_csrf="
                    + URLEncoder.encode(matcher.group(1), StandardCharsets.UTF_8);
            var signedIn = browser.send(HttpRequest.newBuilder(base.resolve("/login"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(signedIn.statusCode()).isEqualTo(302);
            assertThat(signedIn.headers().firstValue("location").map(base::resolve)).hasValue(base.resolve("/"));
            var denied = browser.send(HttpRequest.newBuilder(base.resolve("/admin/users"))
                    .header("Accept", "text/html").GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(denied.statusCode()).isEqualTo(403);
            assertThat(denied.body()).contains("You cannot perform this action.")
                    .doesNotContain("AccessDeniedException", "Whitelabel", "passwordHash", PASSWORD);
            var health = browser.send(HttpRequest.newBuilder(base.resolve("/actuator/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(health.statusCode()).isEqualTo(200);
        }
    }

    private Long create(String username, Role role) {
        CreateUserCommand command = new CreateUserCommand();
        command.setUsername(username);
        command.setDisplayName(username);
        command.setPassword(PASSWORD);
        command.setRole(role);
        return users.createUser(command, null);
    }

    private MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mvc.perform(post("/login").with(csrf())
                        .param("username", username).param("password", PASSWORD))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/"))
                .andExpect(authenticated()).andReturn().getRequest().getSession(false);
    }
}
