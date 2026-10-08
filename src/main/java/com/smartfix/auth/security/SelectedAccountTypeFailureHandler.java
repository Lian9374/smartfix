package com.smartfix.auth.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import java.io.IOException;

/**
 * Sends every failed sign-in to the same page, and tells the account-type refusal apart
 * from a wrong password on the way.
 *
 * <p>Both outcomes are failures and both end on {@code /login} with an error; the only
 * difference is which sentence the page shows. A wrong password says the credentials did
 * not match, because that is all there is to say. A refusal that happened <em>after</em> the
 * password checked out can say something more useful - the details are right, the selected
 * type is not - and it can say it without naming the type the account holds, which is the
 * one fact this case could give away.</p>
 *
 * <h2>Nothing else changes</h2>
 *
 * <p>The refusal is handed to the same machinery as any other failure. Deferring to
 * {@link SimpleUrlAuthenticationFailureHandler} for the default case keeps the redirect
 * strategy, and {@link #saveException} keeps the failure recorded where Spring Security
 * records every other one, so this is a change of destination and not a second way of
 * failing.</p>
 */
public final class SelectedAccountTypeFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    /** Where a password that did not match, or an unusable account, ends up. */
    private static final String DEFAULT_FAILURE_URL = "/login?error";

    /**
     * Where a correct account used through the wrong entry point ends up.
     *
     * <p>A distinct value for {@code error} rather than a second parameter: it is the same
     * kind of thing - a reason the sign-in did not happen - and the page already reads that
     * parameter. The value is a fixed word from this class and is never reflected into the
     * page as text.</p>
     */
    private static final String MISMATCH_FAILURE_URL = "/login?error=type";

    public SelectedAccountTypeFailureHandler() {
        super(DEFAULT_FAILURE_URL);
    }

    /**
     * Sends the refusal back to the sign-in page with the reason, and with the account type
     * the visitor had chosen.
     *
     * <p>Carrying the choice matters most in the mismatch case, which is the one case where
     * it is wrong: the page says "the selected type is not this account's", and the visitor
     * needs to see <em>which</em> type it is talking about without having to remember. The
     * same applies to a mistyped password - the page should come back as it was left.</p>
     *
     * <p>The value appended to the URL is one of the three {@link
     * SelectedAccountType#nameOf} names or absent; the submitted text itself is never used,
     * so this cannot be turned into a redirect to somewhere of the client's choosing. On the
     * page the parameter only decides which segment starts out checked - it grants nothing,
     * and an account used through the wrong segment is still refused.</p>
     *
     * <p>Deferring to the superclass for everything else is kept for the redirect strategy
     * and for {@link #saveException}: a forward is never enabled in this application, so
     * building the destination here and always redirecting is the same journey by the same
     * route, with a different address.</p>
     */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        boolean wrongEntryPoint = exception instanceof SelectedAccountTypeMismatchException;
        saveException(request, exception);
        getRedirectStrategy().sendRedirect(request, response, failureUrl(request, wrongEntryPoint));
    }

    /** The destination: the reason, plus the choice when the request carried a usable one. */
    private static String failureUrl(HttpServletRequest request, boolean wrongEntryPoint) {
        String url = wrongEntryPoint ? MISMATCH_FAILURE_URL : DEFAULT_FAILURE_URL;
        String selected = SelectedAccountType.nameOf(request);
        if (selected == null) {
            return url;
        }
        return url + "&" + SelectedAccountType.FORM_FIELD + "=" + selected;
    }
}
