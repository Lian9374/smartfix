package com.smartfix.reporting.dto;

public record AdminDashboardResponse(
    long totalRequests,
    long openRequests,
    long resolvedRequests,
    long closedRequests,
    double resolutionRatePercent,
    double averageResolutionHours) {
}
