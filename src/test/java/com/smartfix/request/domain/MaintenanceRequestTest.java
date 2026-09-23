package com.smartfix.request.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaintenanceRequestTest {

    private static final Instant SUBMITTED_AT = Instant.parse("2026-09-20T00:00:00Z");

    @Test
    void submitNormalizesTextAndOwnsTheInitialStatus() {
        MaintenanceRequest request = MaintenanceRequest.submit(
                "SF-2026-000001",
                11L,
                21L,
                "  Air conditioner leaking  ",
                "  Water is dripping beside the window.  ",
                MaintenanceCategory.HVAC,
                UrgencyLevel.HIGH,
                SUBMITTED_AT);

        assertThat(request.getTitle()).isEqualTo("Air conditioner leaking");
        assertThat(request.getDescription()).isEqualTo("Water is dripping beside the window.");
        assertThat(request.getRequesterId()).isEqualTo(11L);
        assertThat(request.getLocationId()).isEqualTo(21L);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.SUBMITTED);
        assertThat(request.getCreatedAt()).isEqualTo(SUBMITTED_AT);
        assertThat(request.getUpdatedAt()).isEqualTo(SUBMITTED_AT);
    }

    @Test
    void submitRejectsBlankAndOversizedText() {
        assertThatThrownBy(() -> MaintenanceRequest.submit(
                "SF-2026-000001", 11L, 21L, "   ", "Description",
                MaintenanceCategory.OTHER, UrgencyLevel.LOW, SUBMITTED_AT))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> MaintenanceRequest.submit(
                "SF-2026-000001", 11L, 21L, "Title", "x".repeat(2001),
                MaintenanceCategory.OTHER, UrgencyLevel.LOW, SUBMITTED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void initialHistoryIsAlwaysNullToSubmittedAndAttributesTheActor() {
        RequestStatusHistory history = RequestStatusHistory.initialSubmission(31L, 11L, SUBMITTED_AT);

        assertThat(history.getRequestId()).isEqualTo(31L);
        assertThat(history.getFromStatus()).isNull();
        assertThat(history.getToStatus()).isEqualTo(RequestStatus.SUBMITTED);
        assertThat(history.getChangedByUserId()).isEqualTo(11L);
        assertThat(history.getChangedAt()).isEqualTo(SUBMITTED_AT);
        assertThat(history.getComment()).isNull();
    }
}
