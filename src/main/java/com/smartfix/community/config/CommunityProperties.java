package com.smartfix.community.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Tunable community settings, bound from {@code smartfix.community.*}.
 *
 * <p>ADR-003 (2026-10-08) fixes the defaults: a two-minute duplicate-question
 * window and separate 20-post caps for questions and answers per rolling 24 hours.
 * There is no additional minimum posting interval. These application-level checks
 * are configurable and do not promise a strict concurrent quota.</p>
 *
 * <p>The page sizes are not in that category: default 10 and maximum 50 come from the
 * sprint 3 brief and are treated as fixed.</p>
 */
@Component
@ConfigurationProperties(prefix = "smartfix.community")
public class CommunityProperties {

    private final Page page = new Page();
    private final Posting posting = new Posting();

    public Page getPage() {
        return page;
    }

    public Posting getPosting() {
        return posting;
    }

    /** Page sizes for the community list and "my questions". */
    public static class Page {

        private int defaultSize = 10;
        private int maxSize = 50;

        public int getDefaultSize() {
            return defaultSize;
        }

        public void setDefaultSize(int value) {
            if (value < 1) {
                throw new IllegalArgumentException("smartfix.community.page.default-size must be at least 1");
            }
            defaultSize = value;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int value) {
            if (value < 1) {
                throw new IllegalArgumentException("smartfix.community.page.max-size must be at least 1");
            }
            maxSize = value;
        }

        /** Applies the default and the ceiling to a size a browser asked for. */
        public int resolveSize(int requestedSize) {
            if (requestedSize <= 0) {
                return defaultSize;
            }
            return Math.min(requestedSize, maxSize);
        }
    }

    /** Guards against a question being posted twice, and against posting too often. */
    public static class Posting {

        private boolean duplicateDetectionEnabled = true;
        private Duration duplicateWindow = Duration.ofMinutes(2);
        private Duration rateLimitWindow = Duration.ofHours(24);
        private int maxPerRateLimitWindow = 20;

        public boolean isDuplicateDetectionEnabled() {
            return duplicateDetectionEnabled;
        }

        public void setDuplicateDetectionEnabled(boolean value) {
            duplicateDetectionEnabled = value;
        }

        public Duration getDuplicateWindow() {
            return duplicateWindow;
        }

        public void setDuplicateWindow(Duration value) {
            if (value == null || value.isNegative()) {
                throw new IllegalArgumentException(
                        "smartfix.community.posting.duplicate-window must be nonnegative");
            }
            duplicateWindow = value;
        }

        public Duration getRateLimitWindow() {
            return rateLimitWindow;
        }

        public void setRateLimitWindow(Duration value) {
            if (value == null || value.isNegative() || value.isZero()) {
                throw new IllegalArgumentException(
                        "smartfix.community.posting.rate-limit-window must be positive");
            }
            rateLimitWindow = value;
        }

        public int getMaxPerRateLimitWindow() {
            return maxPerRateLimitWindow;
        }

        public void setMaxPerRateLimitWindow(int value) {
            if (value < 1) {
                throw new IllegalArgumentException(
                        "smartfix.community.posting.max-per-rate-limit-window must be at least 1");
            }
            maxPerRateLimitWindow = value;
        }
    }
}
