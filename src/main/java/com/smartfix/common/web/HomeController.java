package com.smartfix.common.web;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.request.service.RequestQueryService;
import com.smartfix.user.domain.Role;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Authenticated landing page.
 *
 * <p>Its content is decided by what the caller can actually do. A requester sees
 * their own most recent requests, read through {@link RequestQueryService}, whose
 * public contract already scopes the query to the acting user; nothing here
 * reaches past it into a repository, and no other account's data becomes
 * visible. An administrator and a technician get no list, because no service
 * exposes one for them - inventing a query to fill the space would widen what a
 * page can see for the sake of a layout.</p>
 *
 * <p>The role check below is a display decision, not an authorisation one: the
 * routes this page links to are protected by {@code SecurityConfig} whatever it
 * renders.</p>
 */
@Controller
public class HomeController {

    private static final String VIEW_HOME = "home";

    /** Enough to show a pattern of recent activity without becoming a second list page. */
    private static final int RECENT_REQUEST_LIMIT = 5;

    private final RequestQueryService requestQueryService;

    public HomeController(RequestQueryService requestQueryService) {
        this.requestQueryService = requestQueryService;
    }

    @GetMapping({"/", "/home"})
    public String home(@AuthenticationPrincipal SmartFixUserDetails principal, Model model) {
        model.addAttribute("systemName", "SmartFix");
        model.addAttribute("tagline", "Campus Facility Maintenance and Technician Dispatch System");
        model.addAttribute("username", principal.getUsername());
        model.addAttribute("role", principal.getRole().name());

        if (principal.getRole() == Role.REQUESTER) {
            model.addAttribute("recentRequests", requestQueryService.listMyRequests(
                    principal.getUserId(), 0, RECENT_REQUEST_LIMIT));
        }

        return VIEW_HOME;
    }
}
