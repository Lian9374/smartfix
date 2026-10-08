package com.smartfix.auth.security;

import com.smartfix.user.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rule that decides whether a sign-in is refused, tested without a container.
 *
 * <p>The interesting part of the account-type check is not the plumbing that carries the
 * choice from the form to the provider - that is exercised end to end in
 * {@code AuthenticationFlowIT} - but the three-way decision itself: nothing submitted, the
 * account's own type submitted, or something else. The third case includes a value that
 * names no type at all, which is the one that could quietly pass for "nothing submitted" if
 * the two were conflated.</p>
 */
class SelectedAccountTypeTest {

    @Test
    void aRequestWithoutTheFieldAsksForNothing() {
        assertThat(selected("alice", null).contradicts(Role.REQUESTER))
                .as("a client that does not know about the choice signs in as it always did")
                .isFalse();
    }

    @Test
    void aBlankFieldIsTheSameAsAnAbsentOne() {
        assertThat(selected("alice", "   ").contradicts(Role.REQUESTER)).isFalse();
    }

    @Test
    void theAccountsOwnTypeAgrees() {
        for (Role role : Role.values()) {
            assertThat(selected("alice", role.name()).contradicts(role))
                    .as("%s signing in as %s", role, role)
                    .isFalse();
        }
    }

    @Test
    void anyOtherTypeIsARefusal() {
        assertThat(selected("alice", "TECHNICIAN").contradicts(Role.REQUESTER)).isTrue();
        assertThat(selected("alice", "ADMINISTRATOR").contradicts(Role.REQUESTER)).isTrue();
        assertThat(selected("alice", "REQUESTER").contradicts(Role.TECHNICIAN)).isTrue();
    }

    @Test
    void aValueThatNamesNoTypeIsARefusalRatherThanAnAbsence() {
        // The failure this case exists to catch: treating an unrecognised value as "not
        // submitted" would let a disagreement be silently ignored, and the sign-in would
        // succeed on a form the server did not understand.
        assertThat(selected("alice", "SUPERUSER").contradicts(Role.REQUESTER)).isTrue();
        assertThat(selected("alice", "SUPERUSER").selected()).isNull();
    }

    @Test
    void spellingIsNotPartOfTheChoice() {
        assertThat(selected("alice", " technician ").contradicts(Role.TECHNICIAN)).isFalse();
        assertThat(selected("alice", "technician").selected()).isEqualTo(Role.TECHNICIAN);
    }

    @Test
    void theFormFieldIsNamedOnce() {
        assertThat(SelectedAccountType.FORM_FIELD).isEqualTo("accountType");
    }

    /**
     * The refusal is a failed login, and it must not say in logs what the page is not
     * allowed to say either: naming the account's real role would turn a failed sign-in
     * into an answer about the account.
     */
    @Test
    void theRefusalMessageNamesNoRole() {
        String message = new SelectedAccountTypeMismatchException().getMessage();
        assertThat(message).isNotBlank();
        for (Role role : Role.values()) {
            assertThat(message).as("role name in a refusal message").doesNotContain(role.name());
        }
    }

    // ------------------------------------------ the choice, sent back to the browser
    //
    // Two places read it: the failure handler, which puts it in the redirect, and the sign-in
    // page, which checks one segment from it. Both need the same thing - a value taken from a
    // constant, never the text the client sent - and the page additionally needs a value that
    // is never null, because a rendered radio group cannot have "none of the above" as its
    // default.

    @Test
    void theNameOfAChoiceIsAConstantOrNothing() {
        for (Role role : Role.values()) {
            assertThat(SelectedAccountType.nameOf(request(role.name()))).isEqualTo(role.name());
        }
        assertThat(SelectedAccountType.nameOf(request(" technician "))).isEqualTo(Role.TECHNICIAN.name());
        assertThat(SelectedAccountType.nameOf(request(null))).isNull();
        assertThat(SelectedAccountType.nameOf(request("   "))).isNull();
        assertThat(SelectedAccountType.nameOf(request("SUPERUSER"))).isNull();
    }

    @Test
    void theDefaultChoiceIsTheOneAnybodyCanRegisterFor() {
        assertThat(SelectedAccountType.defaultedName(request(null))).isEqualTo(Role.REQUESTER.name());
        assertThat(SelectedAccountType.defaultedName(request("SUPERUSER"))).isEqualTo(Role.REQUESTER.name());
        for (Role role : Role.values()) {
            assertThat(SelectedAccountType.defaultedName(request(role.name()))).isEqualTo(role.name());
        }
    }

    /**
     * Whatever arrives in the field, what is sent back is one of the three constants.
     *
     * <p>This is the property that keeps a submitted string out of a {@code Location} header
     * and out of a rendered attribute: the value is parsed, the parse is discarded, and only
     * a constant can leave.</p>
     */
    @Test
    void whatIsSentBackIsNeverTheSubmittedText() {
        List<String> names = Arrays.stream(Role.values()).map(Enum::name).toList();
        List<String> hostile = List.of("REQUESTER", "requester", "SUPERUSER", "", "   ", "ROLE_ADMIN",
                "\"><script>alert(1)</script>", "ADMINISTRATOR'", "TECHNICIAN%00", "_csrf");
        for (String submitted : hostile) {
            assertThat(SelectedAccountType.defaultedName(request(submitted)))
                    .as("the name sent back for %s", submitted)
                    .isIn(names);
        }
    }

    private SelectedAccountType selected(String username, String accountType) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.addParameter("username", username);
        if (accountType != null) {
            request.addParameter(SelectedAccountType.FORM_FIELD, accountType);
        }
        return new SelectedAccountType(request);
    }

    /** The same request without the wrapper, for the two static readers. */
    private MockHttpServletRequest request(String accountType) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        if (accountType != null) {
            request.addParameter(SelectedAccountType.FORM_FIELD, accountType);
        }
        return request;
    }
}
