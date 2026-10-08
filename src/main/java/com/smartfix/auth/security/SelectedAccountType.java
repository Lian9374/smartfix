package com.smartfix.auth.security;

import com.smartfix.user.domain.Role;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import java.io.Serial;
import java.util.Locale;

/**
 * Which of the three account types the visitor said they were signing in as.
 *
 * <p>The sign-in page asks, because the same form serves three very different people and
 * the choice is what tells a visitor which account to use. It travels as the
 * authentication request's <em>details</em> rather than as a credential, because that is
 * exactly what it is: a statement of intent, placed beside the request and never in
 * place of the account it is checked against.</p>
 *
 * <h2>Why this cannot grant anything</h2>
 *
 * <p>Nothing here is read when authorities are built. The granted authority is
 * {@code SmartFixUserDetails.getAuthorities()}, which is derived from the persisted
 * {@link Role} the account actually has, so a request that says {@code ADMINISTRATOR} on
 * an account that is a requester holds {@code ROLE_REQUESTER} for as long as it exists -
 * and {@link SelectedAccountTypeAuthenticationProvider} makes sure it does not exist at
 * all.</p>
 *
 * <h2>The three cases</h2>
 *
 * <ul>
 *   <li><strong>Nothing submitted.</strong> A client that does not know about the choice -
 *       an older form, a script, a curl one-liner - signs in exactly as it did before the
 *       choice existed. The field is optional, and this is the documented rule for a
 *       request without it.</li>
 *   <li><strong>A submitted type that is the account's own.</strong> Nothing to do.</li>
 *   <li><strong>A submitted type that is not.</strong> Sign-in is refused. This includes
 *       a value that names no type at all: whatever it was, it is not the account's own
 *       type, and pretending not to have seen it would silently downgrade a real
 *       disagreement into a successful sign-in.</li>
 * </ul>
 */
public final class SelectedAccountType extends WebAuthenticationDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The form field the choice is submitted in, named once and referenced by
     * {@code login.html} and by the tests that check it. It is deliberately <em>not</em>
     * called {@code role}: a field that claimed to carry a role would invite exactly the
     * mistake this design exists to prevent.
     */
    public static final String FORM_FIELD = "accountType";

    /** Whether the form submitted anything in the field at all. */
    private final boolean submitted;

    /** The parsed type, or {@code null} when the field was absent, empty or unrecognised. */
    private final Role selected;

    /** @param request the sign-in request, read once and never held on to */
    public SelectedAccountType(HttpServletRequest request) {
        super(request);
        String raw = request.getParameter(FORM_FIELD);
        this.submitted = raw != null && !raw.isBlank();
        this.selected = parse(raw);
    }

    /**
     * @param actual the account's own role, from the database
     * @return whether the visitor asked for a different account type than this one
     */
    public boolean contradicts(Role actual) {
        return submitted && selected != actual;
    }

    /** @return the selected type, or {@code null} when none was submitted or recognised */
    public Role selected() {
        return selected;
    }

    /**
     * The selected type as one of the {@link Role} constants' names, or {@code null} when the
     * request named none.
     *
     * <p>Read by the two places that send the choice back to the browser - the failure
     * handler's redirect and the sign-in page's checked segment - and both of them need the
     * same guarantee: the string is taken from a constant, never from the request text. The
     * submitted value is parsed and then discarded, so nothing a client typed can reach a
     * {@code Location} header or a rendered attribute.</p>
     *
     * @param request the request whose {@value #FORM_FIELD} parameter is read
     * @return {@code REQUESTER}, {@code TECHNICIAN} or {@code ADMINISTRATOR}, or {@code null}
     */
    public static String nameOf(HttpServletRequest request) {
        Role selected = parse(request.getParameter(FORM_FIELD));
        return selected == null ? null : selected.name();
    }

    /**
     * The same name, with the requester standing in when the request named no usable type.
     *
     * <p>The sign-in page always has exactly one segment checked, so the page needs a value
     * rather than a null: no parameter, an unrecognised one and a hand-written URL all mean
     * the same thing here - start on the requester, the type anybody can register for.</p>
     *
     * @param request the request to read the choice from
     * @return a name that is always one of the three role names
     */
    public static String defaultedName(HttpServletRequest request) {
        String named = nameOf(request);
        return named == null ? Role.REQUESTER.name() : named;
    }

    /**
     * Accepts any spelling of a role name, because capitalisation of a form value is not
     * something a person types and the page always sends the constant.
     *
     * <p>An unrecognised value yields {@code null} rather than an exception: it is not an
     * error in the request, it is a type that does not exist, and
     * {@link #contradicts(Role)} treats it the same way as any other type that is not the
     * account's own.</p>
     */
    private static Role parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Role.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException notARole) {
            return null;
        }
    }

    /** Prints the choice only; the value is not a secret, but it is also not the account. */
    @Override
    public String toString() {
        return "SelectedAccountType{selected=" + selected + ", submitted=" + submitted + '}';
    }
}
