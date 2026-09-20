package com.smartfix.request.service;

import com.smartfix.request.domain.RequestTicketSequence;
import com.smartfix.request.repository.RequestTicketSequenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestTicketNumberGeneratorTest {

    private RequestTicketSequenceRepository sequenceRepository;

    @BeforeEach
    void setUp() {
        sequenceRepository = mock(RequestTicketSequenceRepository.class);
    }

    @Test
    void formatsAndAdvancesTheLockedYearlySequence() {
        RequestTicketSequence sequence = RequestTicketSequence.startForYear(2026);
        when(sequenceRepository.findByTicketYearForUpdate(2026)).thenReturn(Optional.of(sequence));
        RequestTicketNumberGenerator generator = generatorAt("2026-09-20T00:00:00Z");

        assertThat(generator.nextTicketNumber()).isEqualTo("SF-2026-000001");
        assertThat(generator.nextTicketNumber()).isEqualTo("SF-2026-000002");

        var ordered = inOrder(sequenceRepository);
        ordered.verify(sequenceRepository).ensureYearExists(2026);
        ordered.verify(sequenceRepository).findByTicketYearForUpdate(2026);
        ordered.verify(sequenceRepository).ensureYearExists(2026);
        ordered.verify(sequenceRepository).findByTicketYearForUpdate(2026);
    }

    @Test
    void usesTheSingaporeYearAtTheUtcNewYearBoundary() {
        RequestTicketSequence sequence = RequestTicketSequence.startForYear(2027);
        when(sequenceRepository.findByTicketYearForUpdate(2027)).thenReturn(Optional.of(sequence));
        RequestTicketNumberGenerator generator = generatorAt("2026-12-31T16:00:00Z");

        assertThat(generator.nextTicketNumber()).isEqualTo("SF-2027-000001");
    }

    @Test
    void failsRatherThanGuessingWhenTheCounterCannotBeLoaded() {
        when(sequenceRepository.findByTicketYearForUpdate(2026)).thenReturn(Optional.empty());
        RequestTicketNumberGenerator generator = generatorAt("2026-09-20T00:00:00Z");

        assertThatThrownBy(generator::nextTicketNumber)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("counter");
    }

    private RequestTicketNumberGenerator generatorAt(String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        return new RequestTicketNumberGenerator(sequenceRepository, clock);
    }
}
