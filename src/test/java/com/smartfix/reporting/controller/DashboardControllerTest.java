package com.smartfix.reporting.controller;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.reporting.dto.AdminDashboardResponse;
import com.smartfix.reporting.dto.TechnicianDashboardResponse;
import com.smartfix.reporting.service.DashboardService;
import com.smartfix.reporting.service.ReportingPeriodService;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.UserAuthenticationData;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = DashboardController.class,
    excludeAutoConfiguration =
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private ReportingPeriodService reportingPeriodService;

    @MockitoBean
    private Clock clock;

    @Test
    void adminDashboardUsesSingaporeReportingPeriod() throws Exception {
        Instant now = Instant.parse("2026-10-09T02:00:00Z");

        when(clock.withZone(ZoneId.of("Asia/Singapore")))
            .thenReturn(Clock.fixed(
                now,
                ZoneId.of("Asia/Singapore")));

        LocalDate startDate = LocalDate.of(2026, 10, 1);
        LocalDate endDate = LocalDate.of(2026, 10, 9);

        Instant start = Instant.parse("2026-09-30T16:00:00Z");
        Instant end = Instant.parse("2026-10-09T16:00:00Z");

        when(reportingPeriodService.toPeriod(startDate, endDate))
            .thenReturn(
                new ReportingPeriodService.ReportingPeriod(
                    start, end));

        var dashboard = new AdminDashboardResponse(
            10, 6, 4, 2, 40.0, 8.5);

        when(dashboardService.getAdminDashboard(start, end))
            .thenReturn(dashboard);

        mvc.perform(get("/dashboard"))
            .andExpect(status().isOk())
            .andExpect(view().name("reporting/dashboard"))
            .andExpect(model().attribute("dashboard", dashboard))
            .andExpect(model().attribute("startDate", startDate))
            .andExpect(model().attribute("endDate", endDate));

        verify(dashboardService).getAdminDashboard(start, end);
    }

    @Test
    void technicianDashboardUsesAuthenticatedUserId() throws Exception {
        Long technicianId = 7L;

        var principal = new SmartFixUserDetails(
            new UserAuthenticationData(
                technicianId,
                "technician",
                "hash",
                Role.TECHNICIAN,
                AccountStatus.ACTIVE,
                0L));

        var dashboard = new TechnicianDashboardResponse(
            technicianId, 3L);

        when(dashboardService.getTechnicianDashboard(technicianId))
            .thenReturn(dashboard);

        mvc.perform(get("/technician/dashboard")
                .with(user(principal)))
            .andExpect(status().isOk())
            .andExpect(view().name("reporting/technician-dashboard"))
            .andExpect(model().attribute("dashboard", dashboard));

        verify(dashboardService)
            .getTechnicianDashboard(technicianId);
    }
}
