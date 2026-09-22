package com.smartfix.user.config;

import com.smartfix.user.service.UserBootstrapService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs the bootstrap step once, after the application context is ready.
 *
 * <p>An {@link ApplicationRunner} rather than a {@code @PostConstruct} or a bean
 * initialiser: those run while the context is still being built, before the transaction
 * infrastructure and the JPA repositories are reliably usable. This runs after startup
 * has completed, which is the earliest point at which writing an account is safe.</p>
 *
 * <p>Deliberately a one-line adapter. All the behaviour lives in
 * {@link UserBootstrapService}, where it can be tested without starting an application
 * context.</p>
 */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private final UserBootstrapService userBootstrapService;

    public BootstrapAdminInitializer(UserBootstrapService userBootstrapService) {
        this.userBootstrapService = userBootstrapService;
    }

    @Override
    public void run(ApplicationArguments args) {
        userBootstrapService.initialize();
    }
}
