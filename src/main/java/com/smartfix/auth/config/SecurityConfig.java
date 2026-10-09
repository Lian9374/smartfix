package com.smartfix.auth.config;

import com.smartfix.auth.security.ActiveAccountFilter;
import com.smartfix.auth.security.SelectedAccountType;
import com.smartfix.auth.security.SelectedAccountTypeAuthenticationProvider;
import com.smartfix.auth.security.SelectedAccountTypeFailureHandler;
import com.smartfix.auth.service.SmartFixUserDetailsService;
import com.smartfix.user.service.UserService;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;

/**
 * Session authentication and the Sprint 3 route matrix. Object ownership stays in business services.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final String TECHNICIAN = "TECHNICIAN";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            SmartFixUserDetailsService userDetailsService, PasswordEncoder passwordEncoder,
            UserService userService) throws Exception {
        DaoAuthenticationProvider provider = new SelectedAccountTypeAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        http.authenticationProvider(provider)
                .csrf(Customizer.withDefaults())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/login", "/actuator/health",
                                "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/login").permitAll()
                        // The one anonymous route that writes. Both methods, because a form
                        // has to be fetched before it can be posted, and nothing else is
                        // opened with it: /register is named exactly, so no prefix of the
                        // account module leaks. CSRF still applies to the POST - the page
                        // carries the token like every other write does.
                        .requestMatchers(HttpMethod.GET, "/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/register").permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/home", "/campus-map").authenticated()

                        .requestMatchers(HttpMethod.GET, "/dashboard")
                        .hasRole("ADMINISTRATOR")

                        .requestMatchers(HttpMethod.GET, "/technician/dashboard")
                        .hasRole("TECHNICIAN")

                        .requestMatchers(HttpMethod.POST, "/logout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/technician/profile").hasRole(TECHNICIAN)
                        .requestMatchers(HttpMethod.POST, "/technician/profile").hasRole(TECHNICIAN)
                        // Frozen Sprint 3 A/D/E contracts; services still enforce authorship/recipients.
                        .requestMatchers(HttpMethod.GET, "/community", "/community/mine", "/community/questions/new",
                                "/community/questions/*", "/community/questions/*/edit", "/community/answers/*/edit",
                                "/notifications", "/announcements").authenticated()
                        .requestMatchers(HttpMethod.POST, "/community/questions", "/community/questions/*",
                                "/community/questions/*/withdraw", "/community/questions/*/answers",
                                "/community/answers/*", "/community/answers/*/withdraw",
                                "/community/questions/*/answers/*/accept", "/community/questions/*/acceptance/remove",
                                "/community/questions/*/reports", "/community/answers/*/reports",
                                "/notifications/*/read").authenticated()
                        // Specific routes precede /requests/*: ADMIN cannot open the submission form.
                        .requestMatchers(HttpMethod.GET, "/requests/new", "/requests/mine")
                            .hasRole("REQUESTER")
                        .requestMatchers(HttpMethod.POST, "/requests").hasRole("REQUESTER")
                        .requestMatchers(HttpMethod.GET, "/requests/*/review").hasRole("ADMINISTRATOR")
                        .requestMatchers(HttpMethod.POST, "/requests/*/review", "/requests/*/close").hasRole("ADMINISTRATOR")
                        .requestMatchers(HttpMethod.POST, "/requests/*/confirm", "/requests/*/feedback", "/requests/*/reopen", "/requests/*/cancel")
                            .hasRole("REQUESTER")
                        .requestMatchers(HttpMethod.GET, "/workorders/mine", "/workorders/*").hasRole(TECHNICIAN)
                        .requestMatchers(HttpMethod.POST, "/workorders/*/accept", "/workorders/*/records", "/workorders/*/complete")
                            .hasRole(TECHNICIAN)
                        .requestMatchers(HttpMethod.GET, "/requests/*", "/requests/*/attachments/*")
                            .hasAnyRole("REQUESTER", "ADMINISTRATOR", TECHNICIAN)
                        .requestMatchers(HttpMethod.GET, "/admin/requests", "/admin/requests/*/dispatch")
                            .hasRole("ADMINISTRATOR")
                        .requestMatchers(HttpMethod.POST, "/admin/requests/*/assign", "/admin/requests/*/reassign", "/admin/requests/*/withdraw")
                            .hasRole("ADMINISTRATOR")
                        // Do not let the legacy /admin/** rule authorize other dispatch methods.
                        .requestMatchers("/admin/requests/*/dispatch", "/admin/requests/*/assign",
                                "/admin/requests/*/reassign", "/admin/requests/*/withdraw").denyAll()
                        .requestMatchers("/admin/**").hasRole("ADMINISTRATOR")
                        .requestMatchers(HttpMethod.GET, "/actuator/info").hasRole("ADMINISTRATOR")
                        // Sprint 3 community: every route is for signed-in users of any role.
                        // Two exceptions sit outside this pattern and are matched above:
                        // the moderation routes - GET /admin/community/reports and the five
                        // POSTs beside it - are administrator-only through /admin/**, and
                        // the two report routes (POST /community/questions/{id}/reports and
                        // POST /community/answers/{id}/reports) are ordinary signed-in
                        // routes, matched here. Deliberately .authenticated() rather than
                        // permitAll: a signed-out visitor must not reach the board at all, and
                        // ownership is checked again in the service layer.
                        .requestMatchers("/community/**").authenticated()
                        .requestMatchers("/notifications", "/notifications/*/read").authenticated()
                        .anyRequest().denyAll())
                .formLogin(login -> login.loginPage("/login")
                        // The account-type choice rides along as the request's details. It is
                        // read once, from the submitted field, and is never a credential: the
                        // role the session ends up with still comes from the database.
                        .authenticationDetailsSource(SelectedAccountType::new)
                        // Distinguishes "the selected type is not this account's" from every
                        // other failure on the way to the same page. Both are refused logins.
                        // Deliberately no failureUrl(...) after this: that call installs a
                        // plain SimpleUrlAuthenticationFailureHandler, which would replace
                        // this one and quietly drop the distinction. The handler's own default
                        // is the same "/login?error" every other failure already used.
                        .failureHandler(new SelectedAccountTypeFailureHandler())
                        .defaultSuccessUrl("/", true))
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID"))
                .exceptionHandling(errors -> errors
                        .accessDeniedHandler((request, response, exception) -> response.sendError(403)))
                // No servlet bean: run once, after session loading and before CSRF/logout/authorization.
                .addFilterAfter(new ActiveAccountFilter(userService), SecurityContextHolderFilter.class);
        return http.build();
    }
}
