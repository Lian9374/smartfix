package com.smartfix.reporting.service;

import com.smartfix.reporting.dto.OperationalReportResponse;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class ReportExportService {

    private static final byte[] UTF8_BOM =
        new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    public byte[] exportCsv(OperationalReportResponse report) {
        StringBuilder csv = new StringBuilder();

        // Fixed column order required by AC-23.
        csv.append(
            "Start,End,Total Requests,Open Requests,Resolved Requests,"
                + "Closed Requests,Resolution Rate (%),"
                + "Average Resolution Hours\r\n");

        csv.append(report.start()).append(',');
        csv.append(report.end()).append(',');
        csv.append(report.totalRequests()).append(',');
        csv.append(report.openRequests()).append(',');
        csv.append(report.resolvedRequests()).append(',');
        csv.append(report.closedRequests()).append(',');
        csv.append(formatDecimal(report.resolutionRatePercent())).append(',');
        csv.append(formatDecimal(report.averageResolutionHours()));
        csv.append("\r\n");

        csv.append("\r\n");
        csv.append("Status,Requests\r\n");

        report.requestsByStatus().forEach((status, count) ->
            csv.append(status)
                .append(',')
                .append(count)
                .append("\r\n"));

        csv.append("\r\n");
        csv.append("Category,Requests\r\n");

        report.requestsByCategory().forEach((category, count) ->
            csv.append(category)
                .append(',')
                .append(count)
                .append("\r\n"));

        csv.append("\r\n");
        csv.append("Urgency,Requests\r\n");

        report.requestsByUrgency().forEach((urgency, count) ->
            csv.append(urgency)
                .append(',')
                .append(count)
                .append("\r\n"));

        byte[] content =
            csv.toString().getBytes(StandardCharsets.UTF_8);

        byte[] result =
            new byte[UTF8_BOM.length + content.length];

        System.arraycopy(
            UTF8_BOM,
            0,
            result,
            0,
            UTF8_BOM.length);

        System.arraycopy(
            content,
            0,
            result,
            UTF8_BOM.length,
            content.length);

        return result;
    }

    private String formatDecimal(double value) {
        return String.format(
            java.util.Locale.ROOT,
            "%.2f",
            value);
    }
}
