package com.smartfix.common.web;

import com.smartfix.auth.security.SmartFixUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;

/**
 * Supplies the shared page shell with the two facts it needs about the caller:
 * the role, so the navigation only offers pages that role can actually open, and
 * the account name, for the topbar.
 *
 * <p>This is a presentation concern, and a global one: the sidebar and topbar are
 * the same fragments on every page, so every page needs the same two attributes.
 * Adding them per controller would repeat the same three lines in each one and
 * still leave the next page to remember them; a single advice keeps the shell
 * working for controllers that do not exist yet.</p>
 *
 * <p>Nothing is queried and no business rule is applied here. Both values come
 * from the authenticated principal, which is already in the session - so this
 * adds no database work to any request and cannot disagree with the
 * authorisation decision Spring Security has already made. Note what is
 * deliberately absent: the account's display name is not on the principal
 * ({@code UserAccessResponse} carries only what an authorisation decision needs),
 * so the topbar shows the account name rather than inventing one.</p>
 *
 * <p>On an anonymous request - the sign-in page, or a 404 for a visitor - no
 * principal is present, no attribute is added, and the shell renders without
 * them. Templates must therefore treat both attributes as optional, and must
 * never treat {@code navRole} as an authorisation check: it decides what the
 * navigation shows, while the routes themselves stay protected by
 * {@code SecurityConfig}.</p>
 */
@ControllerAdvice
public class NavigationAdvice {

    /** Model attribute holding the signed-in role's name, or absent when anonymous. */
    public static final String ROLE_ATTRIBUTE = "navRole";

    /** Model attribute holding the signed-in account name, or absent when anonymous. */
    public static final String USERNAME_ATTRIBUTE = "navUsername";

    @ModelAttribute
    public void addNavigationContext(Model model, Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof SmartFixUserDetails details) {
            model.addAttribute(ROLE_ATTRIBUTE, details.getRole().name());
            model.addAttribute(USERNAME_ATTRIBUTE, details.getUsername());
        }
    }
}
