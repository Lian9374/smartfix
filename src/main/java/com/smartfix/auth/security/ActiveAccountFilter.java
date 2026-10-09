package com.smartfix.auth.security;

import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.dto.UserAccessResponse;
import com.smartfix.user.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Re-checks persisted account access on every authenticated request. */
public class ActiveAccountFilter extends OncePerRequestFilter {
    private final UserService userService;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
    private final CookieClearingLogoutHandler cookieHandler = new CookieClearingLogoutHandler("JSESSIONID");

    public ActiveAccountFilter(UserService userService) {
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && !isCurrent(authentication, request)) {
            logoutHandler.logout(request, response, authentication);
            cookieHandler.logout(request, response, authentication);
            response.sendRedirect(response.encodeRedirectURL(request.getContextPath() + "/login?expired"));
            return;
        }
        if (Boolean.TRUE.equals(request.getAttribute("passwordChangeRequired")) && !passwordRoute(request)) {
            if ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))
                response.sendRedirect(response.encodeRedirectURL(request.getContextPath() + "/account/password"));
            else response.sendError(403, "Change your temporary password before continuing.");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isCurrent(Authentication authentication, HttpServletRequest request) {
        if (!(authentication.getPrincipal() instanceof SmartFixUserDetails principal)) {
            return false;
        }
        try {
            UserAccessResponse access = userService.getUserAccess(principal.getUserId());
            request.setAttribute("passwordChangeRequired", access.passwordChangeRequired());
            return access.accountStatus() == AccountStatus.ACTIVE
                    && access.securityVersion() == principal.getSecurityVersion()
                    && access.role() == principal.getRole();
        } catch (ResourceNotFoundException exception) {
            return false;
        }
        // Database failures propagate: an unavailable check must never grant access.
    }

    private boolean passwordRoute(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals("/account/password") || path.equals("/logout") || path.equals("/login")
                || path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/");
    }
}
