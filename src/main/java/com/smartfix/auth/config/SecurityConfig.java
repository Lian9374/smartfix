package com.smartfix.auth.config;

import com.smartfix.auth.security.ActiveAccountFilter;
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
 * Session authentication and the Sprint 2 route matrix. Resource ownership stays in D's service.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            SmartFixUserDetailsService userDetailsService, PasswordEncoder passwordEncoder,
            UserService userService) throws Exception {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
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
                        .requestMatchers(HttpMethod.GET, "/", "/home").authenticated()
                        .requestMatchers(HttpMethod.POST, "/logout").authenticated()
                        // Specific routes precede /requests/*: ADMIN cannot open the submission form.
                        .requestMatchers(HttpMethod.GET, "/requests/new", "/requests/mine")
                            .hasRole("REQUESTER")
                        .requestMatchers(HttpMethod.POST, "/requests").hasRole("REQUESTER")
                        .requestMatchers(HttpMethod.GET, "/requests/*", "/requests/*/attachments/*")
                            .hasAnyRole("REQUESTER", "ADMINISTRATOR")
                        .requestMatchers("/admin/**").hasRole("ADMINISTRATOR")
                        .requestMatchers(HttpMethod.GET, "/actuator/info").hasRole("ADMINISTRATOR")
                        .anyRequest().denyAll())
                .formLogin(login -> login.loginPage("/login")
                        .defaultSuccessUrl("/", true).failureUrl("/login?error"))
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID"))
                .exceptionHandling(errors -> errors
                        .accessDeniedHandler((request, response, exception) -> response.sendError(403)))
                // No servlet bean: run once, after session loading and before CSRF/logout/authorization.
                .addFilterAfter(new ActiveAccountFilter(userService), SecurityContextHolderFilter.class);
        return http.build();
    }
}
