package com.smartfix.cloud;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Configuration
@Profile("distributed")
public class DistributedSecurity {
    @Bean @Order(1)
    SecurityFilterChain internal(HttpSecurity http, @Value("${smartfix.cloud.internal-token}") String token) throws Exception {
        if (token.length() < 32) throw new IllegalArgumentException("Internal token must have at least 32 characters");
        var expected = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
        return http.securityMatcher("/internal/**").csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .addFilterBefore(new OncePerRequestFilter() {
                    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                            throws ServletException, IOException {
                        var actual = req.getHeader("Authorization");
                        if (actual == null || !MessageDigest.isEqual(expected, actual.getBytes(StandardCharsets.UTF_8))) {
                            res.sendError(401); return;
                        }
                        chain.doFilter(req, res);
                    }
                }, UsernamePasswordAuthenticationFilter.class).build();
    }
    // Management port is reachable only inside the Compose network / task security group.
    @Bean @Order(2)
    SecurityFilterChain monitoring(HttpSecurity http) throws Exception {
        return http.securityMatcher("/actuator/prometheus", "/actuator/health/**")
                .authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
    }
}
