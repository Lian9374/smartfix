package com.smartfix;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Infrastructure-level test: verifies that the Spring application context
 * (configuration, security baseline, datasource, JPA, Flyway wiring) can be
 * constructed. Uses the isolated H2 test profile - no external database needed.
 *
 * <p>Deliberately no business-function tests yet: business behaviour will be
 * added alongside future Jira stories and will focus especially on technician
 * assignment rules, SLA conditions, request state transitions and
 * authorization-sensitive behaviour.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class SmartFixApplicationTests {

    @Test
    @DisplayName("Spring application context loads")
    void contextLoads() {
    }
}
