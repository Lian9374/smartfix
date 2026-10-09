package com.smartfix.common.web;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.request.service.RequestAssignmentAccessService;
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
 *
 * <p>A technician gets the work-order entry point and one honest fact about it:
 * whether the assignment adapter that turns an assigned request into a work
 * order exists yet. {@link RequestAssignmentAccessService#isAvailable()} answers
 * that from the container, so the page states the real dependency instead of a
 * hard-coded "not available" that would outlive it. No work order is read and no
 * count is invented - a page that showed a number would be showing one it cannot
 * obtain.</p>
 */
@Controller
public class HomeController {

    private static final String VIEW_HOME = "home";

    /** Enough to show a pattern of recent activity without becoming a second list page. */
    private static final int RECENT_REQUEST_LIMIT = 5;

    private final RequestQueryService requestQueryService;

    private final RequestAssignmentAccessService assignments;

    public HomeController(
            RequestQueryService requestQueryService, RequestAssignmentAccessService assignments) {
        this.requestQueryService = requestQueryService;
        this.assignments = assignments;
    }

    @GetMapping({"/", "/home"})
    public String home(@AuthenticationPrincipal SmartFixUserDetails principal, Model model) {
        if (principal.getRole() == Role.ADMINISTRATOR) return "redirect:/admin";
        if (principal.getRole() == Role.TECHNICIAN) return "redirect:/technician";
        model.addAttribute("systemName", "SmartFix");
        model.addAttribute("tagline", "Campus Facility Maintenance and Technician Dispatch System");
        model.addAttribute("username", principal.getUsername());
        model.addAttribute("role", principal.getRole().name());

        if (principal.getRole() == Role.REQUESTER) {
            model.addAttribute("recentRequests", requestQueryService.listMyRequests(
                    principal.getUserId(), 0, RECENT_REQUEST_LIMIT));
        }

        // A capability flag, not data: it says whether dispatch can create work
        // orders at all, so the technician's card can name the missing link
        // rather than claim the feature does not exist.
        model.addAttribute("assignmentWired", assignments.isAvailable());

        return VIEW_HOME;
    }
}
