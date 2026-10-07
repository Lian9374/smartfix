package com.smartfix.request.domain;

import static org.assertj.core.api.Assertions.*;

import com.smartfix.user.domain.Role;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RequestTransitionTest {
    @Test
    void preventsSkippingReviewAndResolution() {
        assertThat(
                        RequestTransition.allows(
                                RequestStatus.SUBMITTED,
                                RequestStatus.RESOLVED,
                                Role.ADMINISTRATOR))
                .isFalse();
        assertThat(
                        RequestTransition.allows(
                                RequestStatus.ASSIGNED, RequestStatus.CONFIRMED, Role.REQUESTER))
                .isFalse();
        assertThat(
                        RequestTransition.allows(
                                RequestStatus.RESOLVED, RequestStatus.CLOSED, Role.ADMINISTRATOR))
                .isFalse();
    }

    @ParameterizedTest
    @EnumSource(
            value = RequestStatus.class,
            names = {"CLOSED", "REJECTED", "CANCELLED"})
    void terminalStatesAreImmutable(RequestStatus terminal) {
        for (var target : RequestStatus.values())
            for (var role : Role.values()) {
                assertThat(RequestTransition.allows(terminal, target, role)).isFalse();
            }
    }

    @Test
    void eachActionBelongsToItsRole() {
        assertThat(
                        RequestTransition.allows(
                                RequestStatus.UNDER_REVIEW, RequestStatus.ASSIGNED, Role.REQUESTER))
                .isFalse();
        assertThat(
                        RequestTransition.allows(
                                RequestStatus.RESOLVED, RequestStatus.CONFIRMED, Role.TECHNICIAN))
                .isFalse();
        assertThat(
                        RequestTransition.allows(
                                RequestStatus.IN_PROGRESS,
                                RequestStatus.RESOLVED,
                                Role.ADMINISTRATOR))
                .isFalse();
        for (var state : RequestStatus.values())
            for (var role : Role.values()) {
                assertThat(RequestTransition.allows(state, state, role)).isFalse();
            }
    }
}
