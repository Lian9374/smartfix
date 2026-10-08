package com.smartfix.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;

/**
 * Resolves the CSRF token of a page that is rendered to a visitor who has no session yet.
 *
 * <p>Thymeleaf normally asks for the token while it is streaming the page, at
 * {@code th:action} on the form. On the two pages a signed-out visitor can reach that is
 * too late: the response buffer is 8KB, the campus drawing fills it before the form is
 * reached, and Tomcat commits the response on that first flush. Creating a session after a
 * commit is illegal, so the render stops at the form's opening tag and the visitor is
 * served a page with no fields and no button - a form that cannot be submitted from.</p>
 *
 * <p>Reading the token before the view starts writing resolves it while the buffer is still
 * empty and a session can still be created. Same token, same repository, same session as
 * before - only the moment of creation moves, and the page's size stops deciding whether it
 * renders.</p>
 *
 * <p>One method rather than one copy per controller: the sign-in page and the registration
 * page both need it, for the same reason, and the second copy is where a subtle fix like
 * this quietly goes missing.</p>
 */
final class CsrfTokens {

    private CsrfTokens() {
        // static-only
    }

    /**
     * Touches the token so that the session backing it exists before anything is written.
     *
     * @param request the request being rendered; without a filter-installed token there is
     *                nothing to resolve, and the page renders as it always did
     */
    static void resolve(HttpServletRequest request) {
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
            token.getToken();
        }
    }
}
