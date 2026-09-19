package com.smartfix.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The application's single password encoder.
 *
 * <p>BCrypt because it is deliberately slow and salts every hash, so two accounts with
 * the same password do not share a hash and a stolen database is expensive to attack
 * offline. The hash is self-describing ({@code $2a$...}), which is why one column holds
 * it without extra metadata.</p>
 *
 * <p>One bean, not one per call site. Constructing an encoder where it is used would
 * scatter the work factor across the codebase and make it possible for two parts of the
 * system to disagree about it.</p>
 *
 * <p>This lives in the {@code user} module because accounts own credentials; category B
 * consumes it for login. The alternative - putting it in {@code SecurityConfig} - would
 * tie how passwords are stored to how URLs are protected, which are separate decisions.</p>
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
