package com.smartfix.auth;

import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.UserAuthenticationData;
import com.smartfix.user.service.UserService;
import com.smartfix.user.validation.PasswordPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The public sign-up loop, end to end on a real container: page, CSRF, validation,
 * persistence, the redirect to sign-in, and the session that sign-in is supposed to
 * produce.
 *
 * <p>Same fixture as {@code AuthenticationFlowIT} and for the same reason - real services,
 * real BCrypt, real filters, H2 standing in for PostgreSQL only - but its own in-memory
 * database, so the two classes cannot see each other's accounts whichever order they
 * run in.</p>
 *
 * <h2>What this class is trying to rule out</h2>
 *
 * <p>A registration route is the one place an anonymous visitor writes to the account
 * table, so the tests are written against the ways it could go wrong rather than against
 * the happy path alone: a form that can name its own role, a duplicate username that
 * reaches the browser as a constraint name, a password echoed back into a refused page,
 * and a sign-up that quietly signs somebody in.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:smartfix-register-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "smartfix.bootstrap-admin.enabled=false",
        "smartfix.registration.rate-limit.max-per-address=200"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Sql("/db/auth-test-schema.sql")
class RegistrationFlowIT {

    // Synthetic test-only credentials. The password satisfies PasswordPolicy by design:
    // 13 characters with letters and digits, so a test that wants it refused has to say
    // why rather than rely on it being weak.
    private static final String PASSWORD = "TestPassword9";
    private static final String WEAK_PASSWORD = "short1";

    @Autowired private MockMvc mvc;
    @Autowired private UserService users;

    // ------------------------------------------------------------------ the page

    /**
     * An anonymous visitor gets the form, with the token it needs to submit it and nothing
     * of the account model in it.
     */
    @Test
    void anAnonymousVisitorGetsTheSignUpForm() throws Exception {
        String page = mvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andReturn().getResponse().getContentAsString();

        assertThat(page)
                .as("the POST is CSRF-protected, so the form has to carry a token")
                .contains("name=\"_csrf\"")
                .as("every field is labelled")
                .contains("for=\"username\"", "for=\"displayName\"", "for=\"password\"", "for=\"confirmPassword\"")
                .as("a browser can autofill the fields it is allowed to")
                .contains("autocomplete=\"username\"", "autocomplete=\"name\"", "autocomplete=\"new-password\"")
                .as("the password rule shown is the rule applied")
                .contains("At least " + PasswordPolicy.MIN_LENGTH + " characters")
                .as("the way back to sign-in")
                .contains("href=\"/login\"");
        assertThat(page)
                .as("no hash, no secret, and no role control the page cannot honour")
                .doesNotContain("$2", PASSWORD)
                .doesNotContain("name=\"role\"", "name=\"accountStatus\"");
    }

    /**
     * The two pages lead to each other, because a visitor lands on the wrong one about half
     * the time.
     */
    @Test
    void theSignUpAndSignInPagesLinkToEachOther() throws Exception {
        mvc.perform(get("/register"))
                .andExpect(content().string(containsString("href=\"/login\"")));
        mvc.perform(get("/login"))
                .andExpect(content().string(containsString("href=\"/register\"")));
    }

    // ----------------------------------------------------------- the guard rails

    /**
     * A submission without a valid token is refused and writes nothing.
     *
     * <p>Permitted anonymously is not the same as unprotected: the route is named in
     * {@code SecurityConfig} so the page can be reached signed out, and CSRF still applies
     * to the POST, which is the whole reason the token is in the markup.</p>
     */
    @Test
    void aSubmissionWithoutCsrfIsRefusedAndCreatesNothing() throws Exception {
        mvc.perform(post("/register")
                        .param("username", "no.token").param("displayName", "No Token")
                        .param("password", PASSWORD).param("confirmPassword", PASSWORD))
                .andExpect(status().isForbidden());
        assertThat(users.listUsers()).as("a refused submission leaves no account behind").isEmpty();
    }

    /**
     * A form that asks for a role, a status, an id and a security version gets none of
     * them.
     *
     * <p>Nothing on the server reads those names - {@code RegistrationCommand} has no field
     * for any of them - so this is not a filter that rejects them but a binding target that
     * does not exist. The assertions are on the account that resulted, because a test that
     * only checked the response could pass while the row said ADMINISTRATOR.</p>
     */
    @Test
    void protectedFieldsOnTheFormCannotBeSubmitted() throws Exception {
        mvc.perform(registerForm("escalate", "Escalate")
                        .param("role", Role.ADMINISTRATOR.name())
                        .param("accountStatus", AccountStatus.DISABLED.name())
                        .param("securityVersion", "99")
                        .param("id", "1"))
                .andExpect(redirectedUrl("/login"));

        UserAuthenticationData created = users.findAuthenticationByUsername("escalate");
        assertThat(created.role()).isEqualTo(Role.REQUESTER);
        assertThat(created.accountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(created.securityVersion()).isZero();
        assertThat(users.listUsers()).hasSize(1);

        // The role that was asked for is not the role that exists, and the entry point that
        // would have matched the lie is refused.
        mvc.perform(post("/login").with(csrf()).param("username", "escalate")
                        .param("password", PASSWORD).param("accountType", Role.ADMINISTRATOR.name()))
                .andExpect(redirectedUrl("/login?error=type&accountType=ADMINISTRATOR"))
                .andExpect(unauthenticated());
    }

    // ------------------------------------------------------------- the happy path

    /**
     * The whole loop: submit, land on sign-in with the confirmation, and sign in.
     *
     * <p>The redirect is followed rather than asserted as a string. A redirect to a page that
     * does not open is not a working registration, and the message the visitor is promised
     * only exists if the request after the redirect actually renders it.</p>
     */
    @Test
    void aNewAccountIsAnActiveRequesterAndCanSignInAfterwards() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(registerForm("new.user", "New User").session(session))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"))
                .andExpect(unauthenticated());

        // The hash is the only thing stored, and it is a BCrypt hash of the submitted
        // password - not the password.
        UserAuthenticationData created = users.findAuthenticationByUsername("new.user");
        assertThat(created.passwordHash())
                .startsWith("$2")
                .doesNotContain(PASSWORD);
        assertThat(created.role()).isEqualTo(Role.REQUESTER);
        assertThat(created.accountStatus()).isEqualTo(AccountStatus.ACTIVE);

        // Following the redirect for real, with the session the flash attribute was saved in.
        mvc.perform(get("/login").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Account created. Sign in to continue.")));

        MockHttpSession signedIn = (MockHttpSession) mvc.perform(post("/login").with(csrf())
                        .param("username", "new.user").param("password", PASSWORD)
                        .param("accountType", Role.REQUESTER.name()))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated())
                .andReturn().getRequest().getSession(false);

        // A requester account reaches the requester's pages and the community board, and
        // nothing more.
        mvc.perform(get("/home").session(signedIn)).andExpect(status().isOk());
        mvc.perform(get("/community").session(signedIn)).andExpect(status().isOk());
        mvc.perform(get("/requests/new").session(signedIn)).andExpect(status().isOk());
        mvc.perform(get("/admin/users").session(signedIn)).andExpect(status().isForbidden());
    }

    /**
     * A username typed in capitals is stored the way every other account stores it, so
     * signing in later does not depend on remembering how it was typed.
     */
    @Test
    void aUsernameIsNormalizedBeforeItIsStored() throws Exception {
        mvc.perform(registerForm("New.User", "New User")).andExpect(redirectedUrl("/login"));
        assertThat(users.findAuthenticationByUsername("NEW.USER").username()).isEqualTo("new.user");
        mvc.perform(post("/login").with(csrf()).param("username", " new.user ")
                        .param("password", PASSWORD).param("accountType", Role.REQUESTER.name()))
                .andExpect(redirectedUrl("/")).andExpect(authenticated());
    }

    // ---------------------------------------------------------- the refusals

    /**
     * A taken username is reported as a field-level message, in words, with the form still
     * filled in.
     *
     * <p>The unique index is what settles a race between two people typing the same name,
     * so this path is reachable in normal use and must not be the one that shows a stack
     * trace. The assertion that no constraint name and no SQL appears in the response is
     * the point of the test.</p>
     */
    @Test
    void aTakenUsernameIsRefusedInWords() throws Exception {
        mvc.perform(registerForm("taken.name", "First")).andExpect(redirectedUrl("/login"));
        String page = mvc.perform(registerForm("taken.name", "Second"))
                .andExpect(status().isConflict())
                .andExpect(view().name("register"))
                .andReturn().getResponse().getContentAsString();

        assertThat(page)
                .as("the visitor is told what to change")
                .contains("An account with this username already exists.")
                .as("and the form still holds what they typed")
                .contains("value=\"taken.name\"", "value=\"Second\"");
        assertThat(page)
                .as("no SQL, no constraint name, no stack trace, no hash")
                .doesNotContain("uk_users_username", "Unique index", "INSERT INTO", "org.h2",
                        "could not execute statement", "$2",
                        "DataIntegrityViolationException", "BusinessConflictException")
                .doesNotContain(PASSWORD);
        assertThat(users.listUsers()).as("the refused attempt changed nothing").hasSize(1);
    }

    /**
     * A username the account model does not accept is refused by the form.
     *
     * <p>Asserted on the opening of the message rather than on the whole of it. The full
     * sentence contains apostrophes around '.', '_' and '-', and the message goes through
     * {@code java.text.MessageFormat} on its way to the page - where a single quote is a
     * quoting marker, not a character - so what a reader actually sees is
     * "digits, ., _ or -.". That is a property of the shared message string, which the
     * administrator's form uses too, and not something this route introduced; the rule is
     * also stated in full and unaltered in the hint directly above the field.</p>
     */
    @Test
    void anIllegalUsernameIsRefusedByTheForm() throws Exception {
        String rule = "Username must be 3-50 characters using only letters, digits,";
        assertRefusedWith(mvc.perform(registerForm("ab", "Too Short")), rule);
        assertRefusedWith(mvc.perform(registerForm("has space", "Has Space")), rule);
        assertThat(users.listUsers()).isEmpty();
    }

    /** A password that does not satisfy the shared policy is refused, with the policy's own words. */
    @Test
    void aWeakPasswordIsRefusedWithThePolicysOwnWords() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("username", "weak.password").param("displayName", "Weak Password")
                        .param("password", WEAK_PASSWORD).param("confirmPassword", WEAK_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("register"))
                .andExpect(content().string(containsString(
                        "Password must be at least " + PasswordPolicy.MIN_LENGTH + " characters long.")));
        assertThat(users.listUsers()).isEmpty();
    }

    /** The confirmation is a rule about two fields, so it is reported once, on the second. */
    @Test
    void aMismatchedConfirmationIsRefused() throws Exception {
        String page = mvc.perform(post("/register").with(csrf())
                        .param("username", "mismatch").param("displayName", "Mismatch")
                        .param("password", PASSWORD).param("confirmPassword", PASSWORD + "x"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("register"))
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("Passwords do not match.");
        assertThat(users.listUsers()).isEmpty();
    }

    /**
     * A refused submission keeps what is safe to keep and drops both secrets.
     *
     * <p>Retyping the whole form because one field was wrong is the kind of small cruelty
     * that makes people choose worse passwords; putting the submitted password back into the
     * response is the kind of small leak that puts it in a browser cache. Both are decided
     * here, so neither is left to the template alone.</p>
     */
    @Test
    void aRefusedFormKeepsTheSafeAnswersAndDropsBothSecrets() throws Exception {
        String page = mvc.perform(post("/register").with(csrf())
                        .param("username", "kept.user").param("displayName", "Kept User")
                        .param("password", PASSWORD).param("confirmPassword", PASSWORD + "x"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("value=\"kept.user\"", "value=\"Kept User\"");
        assertThat(page)
                .as("neither secret is written back, in any form")
                .doesNotContain(PASSWORD, PASSWORD + "x", "$2");
    }

    // ---------------------------------------------------------------- helpers

    /**
     * A submission of the form's own four fields, with a valid token.
     *
     * <p>A builder rather than a performed request, so a caller can add the parameters the
     * form does not have - which is how the escalation test is written - or attach the
     * session it wants to follow the redirect with.</p>
     *
     * @param username    the login name to submit
     * @param displayName the display name to submit
     * @return the request, not yet performed
     */
    private MockHttpServletRequestBuilder registerForm(String username, String displayName) {
        return post("/register").with(csrf())
                .param("username", username).param("displayName", displayName)
                .param("password", PASSWORD).param("confirmPassword", PASSWORD);
    }

    private void assertRefusedWith(ResultActions result, String message) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(view().name("register"))
                .andExpect(content().string(containsString(message)))
                .andExpect(content().string(not(containsString("$2"))));
    }
}
