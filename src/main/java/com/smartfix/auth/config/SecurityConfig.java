package com.smartfix.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * TEMPORARY development security baseline for the SmartFix scaffold.
 *
 * <p>Secure sign-in and role-based access control are planned for Sprint 2 and are
 * <strong>not</strong> implemented yet. Until then every request is permitted so that
 * the team can verify the engineering foundation (home page, static resources and the
 * actuator health endpoint).</p>
 *
 * <p>WARNING: this is scaffolding only. It contains no real credentials and no
 * production authentication flow. It must be replaced by a real configuration
 * (form login, password encoder, CSRF, session handling and role-based rules) during
 * Sprint 2. External NUS SSO is intentionally not integrated.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // TODO(Sprint 2): re-enable CSRF once authenticated, state-changing flows exist.
                .csrf(AbstractHttpConfigurer::disable)
                // No authentication mechanism exists yet - disable form/basic login so
                // Spring Security does not register a meaningless default login page.
                // TODO(Sprint 2): re-enable formLogin together with the real UserDetailsService.
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                // TODO(Sprint 2): replace permitAll with role-based access rules
                // (REQUESTER / TECHNICIAN / ADMINISTRATOR) and real login/authorization.
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll());
        return http.build();
    }
}
