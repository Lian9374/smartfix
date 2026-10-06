package com.smartfix.reporting.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportingPeriodServiceTest {

    private final ReportingPeriodService service =
        new ReportingPeriodService();

    @Test
    void convertsSingaporeCalendarDatesToHalfOpenInstantRange() {
        var period = service.toPeriod(
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 31));

        assertEquals(
            Instant.parse("2026-09-30T16:00:00Z"),
            period.start());

        assertEquals(
            Instant.parse("2026-10-31T16:00:00Z"),
            period.endExclusive());
    }

    @Test
    void rejectsEndDateBeforeStartDate() {
        assertThrows(
            IllegalArgumentException.class,
            () -> service.toPeriod(
                LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 10, 1)));
    }
}
