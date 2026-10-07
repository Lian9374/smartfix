package com.smartfix.reporting.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class ReportingPeriodService {

    private static final ZoneId REPORTING_ZONE =
        ZoneId.of("Asia/Singapore");

    public ReportingPeriod toPeriod(
        LocalDate startDate,
        LocalDate endDate) {

        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException(
                "Start date and end date are required.");
        }

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                "End date must not be before start date.");
        }

        Instant start = startDate
            .atStartOfDay(REPORTING_ZONE)
            .toInstant();

        Instant endExclusive = endDate
            .plusDays(1)
            .atStartOfDay(REPORTING_ZONE)
            .toInstant();

        return new ReportingPeriod(
            start,
            endExclusive);
    }

    public record ReportingPeriod(
        Instant start,
        Instant endExclusive) {
    }
}
