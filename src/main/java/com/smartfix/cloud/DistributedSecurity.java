package com.smartfix.cloud;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.web.SecurityFilterChain;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

@Configuration
@Profile("distributed")
public class DistributedSecurity {
    @Bean @Order(1)
    SecurityFilterChain internal(HttpSecurity http, @Value("${smartfix.cloud.internal-token}") String token) {
        if (token.length() < 32) {
            throw new IllegalArgumentException("Internal token must have at least 32 characters");
        }
        var expected = token.getBytes(StandardCharsets.UTF_8);
        try {
            return http.securityMatcher("/internal/**")
                    .csrf(Customizer.withDefaults())
                    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .requestCache(c -> c.disable())
                    .authorizeHttpRequests(a -> a
                            .requestMatchers(HttpMethod.POST, "/internal/deliveries/*/complete")
                            .hasAuthority("DELIVERY_WORKER")
                            .anyRequest().denyAll())
                    // Spring's resource server handles bearer-only CSRF exemptions. Cookie/session
                    // credentials cannot authorize this API; browser forms retain default CSRF.
                    // The shared opaque token is checked locally; no external identity service is called.
                    .oauth2ResourceServer(oauth -> oauth.opaqueToken(opaque -> opaque.introspector(actual -> {
                        if (!MessageDigest.isEqual(expected, actual.getBytes(StandardCharsets.UTF_8))) {
                            throw new BadOpaqueTokenException("Invalid worker credential");
                        }
                        return new DefaultOAuth2AuthenticatedPrincipal("notification-worker",
                                Map.of("sub", "notification-worker"),
                                List.of(new SimpleGrantedAuthority("DELIVERY_WORKER")));
                    })))
                    .build();
        } catch (Exception failure) {
            throw new BeanInitializationException("Unable to configure internal delivery security", failure);
        }
    }
    // Management port is reachable only inside the Compose network / task security group.
    @Bean @Order(2)
    SecurityFilterChain monitoring(HttpSecurity http) {
        try {
            return http.securityMatcher("/actuator/prometheus", "/actuator/health/**")
                    .authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
        } catch (Exception failure) {
            throw new BeanInitializationException("Unable to configure monitoring security", failure);
        }
    }
}
