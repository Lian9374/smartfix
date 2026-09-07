package com.smartfix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * Entry point for the SmartFix Spring Boot application.
 *
 * <p>SmartFix is a campus facility maintenance and technician dispatch system.
 * This source tree currently contains the <em>initial architecture scaffold</em>:
 * infrastructure, configuration, security baseline and a minimal home page.
 * Business functionality is implemented incrementally through Agile sprints
 * following detailed analysis and design.</p>
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} is excluded so that Spring Security
 * does not create a default in-memory user with a random password. No user store exists
 * yet: authentication is deferred to Sprint 2 (see {@code auth.config.SecurityConfig}).</p>
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SmartFixApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartFixApplication.class, args);
    }
}
