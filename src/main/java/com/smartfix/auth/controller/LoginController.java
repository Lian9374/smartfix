package com.smartfix.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Spring Security owns POST /login and POST /logout. */
@Controller
public class LoginController {

    /**
     * Renders the sign-in form, which carries its own CSRF token: Thymeleaf asks
     * for that token while it is streaming the page, at th:action on the form.
     *
     * A visitor arriving without a session has no token yet, so resolving it
     * there is what creates the session - and on this page, by then it is too
     * late. The response buffer is 8KB, the campus drawing fills it before the
     * form is reached, and Tomcat commits the response on that first flush.
     * Creating a session after a commit is illegal, so the render stops at the
     * form's opening tag and the visitor is served a page with no fields and no
     * button: a sign-in page that cannot be signed in from.
     *
     * Reading the token here resolves it before the view starts writing, while
     * the buffer is still empty and a session can still be created. Same token,
     * same repository, same session as before - only the moment of creation
     * moves, and the page's size stops deciding whether it renders.
     */
    @GetMapping("/login")
    public String login(HttpServletRequest request) {
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
            token.getToken();
        }
        return "login";
    }
}
