package com.smartfix.request.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** D-08 remains a team decision. Zero means no time limit before the request is CLOSED. */
@Component
@ConfigurationProperties(prefix = "smartfix.request.workflow")
public class RequestWorkflowProperties {
    private Duration reopenWindow = Duration.ZERO;

    public Duration getReopenWindow() {
        return reopenWindow;
    }

    public void setReopenWindow(Duration value) {
        if (value == null || value.isNegative())
            throw new IllegalArgumentException("reopenWindow must be nonnegative");
        reopenWindow = value;
    }
}
