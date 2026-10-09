package com.smartfix.auth;

import com.smartfix.auth.security.SelectedAccountType;
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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real A services, BCrypt, persistence, B filters and templates; only the database is H2.
 *
 * <p>The overview reads a requester's own most recent requests, so the landing page
 * every sign-in test finishes on needs the request tables to exist. Hibernate builds
 * the whole schema from the entities for that; {@code @Sql} still resets {@code users}
 * before each method, which is what lets {@code accounts()} create the same three
 * usernames every time. The two do not collide: {@code requester_id} and
 * {@code location_id} are plain columns, so dropping {@code users} leaves no foreign
 * key pointing at it.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:smartfix-auth-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
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
        String username = accountOf(role);
        // The interface names a role as a word; the enum keeps its own spelling.
        String roleWord = roleWord(role);
        MockHttpSession session = login(username);
        if (role != Role.REQUESTER) {
            mvc.perform(get("/").session(session)).andExpect(redirectedUrl(role == Role.ADMINISTRATOR ? "/admin" : "/technician"));
            return;
        }
        var result = mvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                // Named as the signed-in account's own role, in the badge the
                // shell renders once for the page.
                .andExpect(content().string(containsString("badge--brand\">" + roleWord + "<")))
                .andExpect(content().string(containsString("Sign out")));
        if (role == Role.TECHNICIAN) {
            // The work-order workspace exists and is authorised for a
            // technician, so the overview links it. What it must still not do is
            // offer the requester-only submission form or the admin area.
            result.andExpect(content().string(containsString("href=\"/workorders/mine\"")))
                    .andExpect(content().string(not(containsString("href=\"/requests/new\""))))
                    .andExpect(content().string(not(containsString("href=\"/admin/users\""))));
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
        mvc.perform(get("/home").session(login("new.user"))).andExpect(redirectedUrl("/account/password"));
        assertThat(users.getUserAccess(users.findAuthenticationByUsername("new.user").userId()).passwordChangeRequired()).isTrue();
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
        // The role changed, so the overview now offers the technician's own
        // destination instead of the requester's.
        mvc.perform(get("/home").session(current))
                .andExpect(redirectedUrl("/technician"));
        // The new role does not widen access to the old one's routes.
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

    // ------------------------------------------------- the account-type entry points
    //
    // The sign-in page asks which of the three kinds of account the visit is for. The
    // choice is an intent and nothing more: it decides whether this sign-in is allowed to
    // happen, never what the session may do afterwards. The tests below are written from
    // both directions - the right entry point signs in and the wrong one does not - because
    // only the pair rules out "the check never runs".

    /**
     * Each of the three accounts signs in through the entry point that names its own role.
     *
     * <p>The home page assertion is the same one the entry-point-free test makes, so a
     * change that broke sign-in for everybody could not pass here by accident.</p>
     */
    @ParameterizedTest
    @EnumSource(Role.class)
    void eachAccountSignsInThroughItsOwnEntryPoint(Role role) throws Exception {
        MockHttpSession session = login(accountOf(role), role.name());
        if (role != Role.REQUESTER) {
            mvc.perform(get("/").session(session)).andExpect(redirectedUrl(role == Role.ADMINISTRATOR ? "/admin" : "/technician"));
            return;
        }
        mvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("badge--brand\">" + roleWord(role) + "<")));
    }

    /**
     * A correct account used through the wrong entry point does not get a session.
     *
     * <p>The refusal has to happen in the authentication flow, and this is what that means
     * in observable terms: the response is a redirect to the sign-in page with the
     * account-type reason, Spring Security reports no authentication, and the session the
     * failure handler touched - it records the exception, as it does for every failed
     * sign-in - is a stranger's session, so the next request to a protected page is
     * redirected to sign in again.</p>
     *
     * <p>The password is the account's real one. That is the point: this is not a bad
     * credential being rejected, it is a good credential being refused because the visitor
     * said they were somebody else.</p>
     */
    @Test
    void choosingAnAccountTypeThatIsNotTheAccountsOwnRefusesTheSignIn() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/login").with(csrf())
                        .param("username", "alice").param("password", PASSWORD)
                        .param("accountType", Role.TECHNICIAN.name()))
                .andExpect(status().isFound())
                // The refusal carries the choice back, so the page can show the visitor the
                // segment they picked rather than silently resetting them to the requester.
                .andExpect(redirectedUrl("/login?error=type&accountType=TECHNICIAN"))
                .andExpect(unauthenticated())
                .andReturn().getRequest().getSession(false);

        // Whatever session exists after that response carries no authentication. Signing in
        // is not "mostly done" at this point; there is nothing to continue from.
        if (session != null) {
            mvc.perform(get("/home").session(session))
                    .andExpect(redirectedUrl("http://localhost/login"));
        }

        mvc.perform(get("/login?error=type"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Unable to sign in with the selected account type. "
                                + "Check your details or select another type.")))
                // The password was right, so the generic sentence would be a lie here - and
                // the two must stay distinguishable, or the reason is decoration.
                .andExpect(content().string(not(containsString("Invalid username or password."))));

        // And the choice the visitor made survives the round trip: the page they land on
        // has the technician segment checked, not the default one.
        assertThat(checkedValue(page("?error=type&accountType=TECHNICIAN")))
                .isEqualTo(Role.TECHNICIAN.name());
    }

    /**
     * A value that names no account type at all is refused, not ignored.
     *
     * <p>Treating it as "the field was not sent" would mean a form the server does not
     * understand signs in anyway, which is the opposite of what the check is for.</p>
     *
     * <p>Nothing of the submitted text comes back either: the redirect names the reason and
     * no type, because {@code SUPERUSER} is not one and the value that is echoed anywhere is
     * taken from a constant.</p>
     */
    @Test
    void aValueThatNamesNoAccountTypeIsRefusedRatherThanIgnored() throws Exception {
        mvc.perform(post("/login").with(csrf())
                        .param("username", "alice").param("password", PASSWORD)
                        .param("accountType", "SUPERUSER"))
                .andExpect(redirectedUrl("/login?error=type"))
                .andExpect(unauthenticated());
        assertThat(checkedValue(page("?error=type")))
                .as("an unusable choice falls back to the default segment")
                .isEqualTo(Role.REQUESTER.name());
    }

    /**
     * A request that does not carry the field signs in exactly as it did before the choice
     * existed.
     *
     * <p>This is the compatibility rule, and it is stated rather than implied: the field is
     * optional, so an older form, a script or a `curl` one-liner keeps working. What the
     * page sends is a choice; what a request omits is not a disagreement.</p>
     */
    @Test
    void aRequestWithoutTheFieldSignsInAsBefore() throws Exception {
        mvc.perform(post("/login").with(csrf())
                        .param("username", "alice").param("password", PASSWORD))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated());
    }

    /**
     * A disabled account is refused even when the entry point is the right one, and it is
     * refused the ordinary way: the same generic sentence a wrong password gets.
     *
     * <p>The choice comes back in the address here too, so the page shows the visitor the
     * segment they picked - which is all it is for. It says nothing about the account: the
     * sentence rendered is the generic one, and a disabled account is not named as such.</p>
     */
    @Test
    void aDisabledAccountIsRefusedThroughItsOwnEntryPointToo() throws Exception {
        ChangeAccountStatusCommand change = new ChangeAccountStatusCommand();
        change.setAccountStatus(AccountStatus.DISABLED);
        users.changeAccountStatus(requesterId, change, null);
        mvc.perform(post("/login").with(csrf())
                        .param("username", "alice").param("password", PASSWORD)
                        .param("accountType", Role.REQUESTER.name()))
                .andExpect(redirectedUrl("/login?error&accountType=REQUESTER"))
                .andExpect(unauthenticated());
        mvc.perform(get("/login?error&accountType=REQUESTER"))
                .andExpect(content().string(containsString("Invalid username or password.")))
                .andExpect(content().string(not(containsString("disabled"))));
        assertThat(checkedValue(page("?error&accountType=REQUESTER"))).isEqualTo(Role.REQUESTER.name());
    }

    /**
     * Adding a {@code role} parameter to the sign-in form changes nothing.
     *
     * <p>Nothing reads that name - the role comes from the account - so the parameter is
     * simply ignored, and the session's authority is the one the database holds. The
     * refusal on {@code /admin/users} is what makes that visible.</p>
     */
    @Test
    void aRoleParameterOnTheSignInFormGrantsNothing() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/login").with(csrf())
                        .param("username", "alice").param("password", PASSWORD)
                        .param("accountType", Role.REQUESTER.name())
                        .param("role", Role.ADMINISTRATOR.name()))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated())
                .andReturn().getRequest().getSession(false);
        mvc.perform(get("/admin/users").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/home").session(session))
                .andExpect(content().string(containsString("badge--brand\">Requester<")));
    }

    /**
     * The page offers exactly the three roles, defaults to the requester, and offers a way
     * to get an account.
     *
     * <p>The values are checked against {@code Role.values()} rather than against three
     * literals, so a role added to the enum and not to the page - or a value the enum does
     * not have - fails here instead of at somebody's sign-in.</p>
     *
     * <p>What this test can see is markup: that the three inputs are one group, that each is
     * a native radio inside the label that names it, and that the server checks one and only
     * one of them. It cannot see whether a click in the corner of a segment lands on the
     * input - that is a layout fact, and the browser run is where it is checked. The
     * assertions here are written so that a change which broke either one would fail
     * somewhere: the nesting in the markup, the grouping in the names, the selection in the
     * server's answer.</p>
     */
    @Test
    void theSignInPageOffersTheThreeRolesAndOnlyTheRequesterCanSignUp() throws Exception {
        String page = page("");

        assertThat(occurrences(page, "name=\"" + SelectedAccountType.FORM_FIELD + "\""))
                .as("one radio per account type, all in the one group")
                .isEqualTo(Role.values().length);
        assertThat(occurrences(page, "type=\"radio\""))
                .as("no other radio on the page belongs to this group")
                .isEqualTo(Role.values().length);
        assertThat(occurrences(page, "value=\"" + SelectedAccountType.FORM_FIELD + "\""))
                .as("the group is not also submitted under a second name")
                .isZero();

        for (Role role : Role.values()) {
            // The whole segment is the target, so the input is inside the label that names
            // it: a click anywhere in the segment activates it, not only the word.
            assertThat(segment(page, role))
                    .as("the %s segment", role)
                    .containsPattern(Pattern.compile("^<label[^>]*for=\"accountType-" + slug(role)
                            + "\"[^>]*>\\s*<input[^>]*id=\"accountType-" + slug(role) + "\"[^>]*>",
                            Pattern.DOTALL))
                    // Each segment carries its own value under the group's own name - the
                    // contract the server reads - and says which one it is.
                    .contains("name=\"" + SelectedAccountType.FORM_FIELD + "\"")
                    .contains("value=\"" + role.name() + "\"")
                    .contains(">" + roleWord(role) + "<");
        }

        // Once the id, the for, the value, the visible word and the checked attribute are
        // set aside, the three segments are the same markup. A per-segment class, an inline
        // style or an "is-active" flag would show up here - and it is exactly that kind of
        // second source of truth that made one option look selected while the radio
        // underneath said otherwise.
        String requester = shapeOf(segment(page, Role.REQUESTER));
        assertThat(shapeOf(segment(page, Role.TECHNICIAN))).isEqualTo(requester);
        assertThat(shapeOf(segment(page, Role.ADMINISTRATOR))).isEqualTo(requester);

        assertThat(checkedValue(page)).as("the default selection").isEqualTo(Role.REQUESTER.name());

        // The way to get an account is one link, under the button rather than inside the
        // bar, and one muted line saying who creates the other two kinds of account.
        assertThat(occurrences(page, "href=\"/register\"")).isEqualTo(1);
        assertThat(page.indexOf("href=\"/register\""))
                .as("the register entry sits below the sign-in button")
                .isGreaterThan(page.indexOf("type=\"submit\""));
        assertThat(page.indexOf("href=\"/register\""))
                .as("and outside the account-type bar")
                .isGreaterThan(page.indexOf("</fieldset>"));
        assertThat(occurrences(page,
                "Technician and administrator accounts are created by an administrator.")).isEqualTo(1);
    }

    /**
     * The server decides which segment is checked, and its answer is always exactly one of
     * the three - including for an address that names none, and for an address that names
     * something that is not a role at all.
     *
     * <p>This is the rule that makes "at most one option can look selected" a property of
     * the response rather than of the browser's guess: the page renders {@code checked} from
     * a value the controller resolved, so there is no state in which two are checked or none
     * is.</p>
     */
    @ParameterizedTest
    @EnumSource(Role.class)
    void exactlyOneSegmentIsCheckedWhateverTheAddressSays(Role role) throws Exception {
        assertThat(checkedValue(page(""))).isEqualTo(Role.REQUESTER.name());
        assertThat(checkedValue(page("?accountType=" + role.name()))).isEqualTo(role.name());
        assertThat(checkedValue(page("?accountType=" + role.name().toLowerCase(Locale.ROOT))))
                .as("the address is not case-sensitive either")
                .isEqualTo(role.name());
        assertThat(checkedValue(page("?accountType=SUPERUSER"))).isEqualTo(Role.REQUESTER.name());
        assertThat(checkedValue(page("?accountType="))).isEqualTo(Role.REQUESTER.name());
    }

    /**
     * The way to register is on the page in every state, and it is a real link to the real
     * page - not a link only the requester's segment carries, and not one a script has to
     * reveal.
     */
    @ParameterizedTest
    @EnumSource(Role.class)
    void theRegisterEntryIsOfferedWhateverSegmentIsSelected(Role role) throws Exception {
        for (String query : new String[] {"?accountType=" + role.name(),
                "?error=type&accountType=" + role.name(), "?error"}) {
            String page = page(query);
            assertThat(occurrences(page, "href=\"/register\""))
                    .as("register links on %s", query)
                    .isEqualTo(1);
            assertThat(page).as("the invitation on %s", query).contains("New to SmartFix?");
            assertThat(page).as("the link text on %s", query).contains(">Create a requester account<");
        }
    }

    private String accountOf(Role role) {
        return switch (role) {
            case REQUESTER -> "alice";
            case TECHNICIAN -> "tech";
            case ADMINISTRATOR -> "root.admin";
        };
    }

    private String roleWord(Role role) {
        return switch (role) {
            case REQUESTER -> "Requester";
            case TECHNICIAN -> "Technician";
            case ADMINISTRATOR -> "Administrator";
        };
    }

    private MockHttpSession login(String username, String accountType) throws Exception {
        return (MockHttpSession) mvc.perform(post("/login").with(csrf())
                        .param("username", username).param("password", PASSWORD)
                        .param(SelectedAccountType.FORM_FIELD, accountType))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/"))
                .andExpect(authenticated()).andReturn().getRequest().getSession(false);
    }

    /** The sign-in page as it is really rendered, address and all. */
    private String page(String query) throws Exception {
        return mvc.perform(get("/login" + query)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static String slug(Role role) {
        return role.name().toLowerCase(Locale.ROOT);
    }

    /** One segment of the bar, from its opening label to its closing one. */
    private static String segment(String page, Role role) {
        Matcher matcher = Pattern.compile(
                        "<label class=\"account-type__option\"[^>]*for=\"accountType-" + slug(role) + "\">.*?</label>",
                        Pattern.DOTALL)
                .matcher(page);
        assertThat(matcher.find()).as("a segment for %s", role).isTrue();
        return matcher.group();
    }

    /** The same markup with everything that is allowed to differ between segments removed. */
    private static String shapeOf(String segment) {
        return segment.replaceAll("accountType-[a-z]+", "accountType-x")
                .replaceAll("value=\"[A-Z]+\"", "value=\"X\"")
                .replaceAll(">(Requester|Technician|Administrator)<", ">Word<")
                .replace(" checked=\"checked\"", "");
    }

    /** Which of the three the rendered page shows as selected; asserting there is exactly one. */
    private static String checkedValue(String page) {
        List<String> selected = Arrays.stream(Role.values())
                .filter(role -> segment(page, role).contains("checked=\"checked\""))
                .map(Enum::name)
                .toList();
        assertThat(selected).as("the segments the page renders as selected").hasSize(1);
        return selected.get(0);
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }
}
