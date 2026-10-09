package com.smartfix.announcement.controller;

import com.smartfix.announcement.dto.AnnouncementResponse;
import com.smartfix.announcement.service.AnnouncementService;
import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.UserAuthenticationData;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AnnouncementController.class)
class AnnouncementControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AnnouncementService announcements;

    @Test
    void publicPageShowsVisibleAnnouncements() throws Exception {
        when(announcements.listVisible()).thenReturn(List.of());

        mvc.perform(get("/announcements")
                .with(user(account(Role.REQUESTER))))
            .andExpect(status().isOk())
            .andExpect(view().name("announcement/list"))
            .andExpect(model().attributeExists("announcements"));

        verify(announcements).listVisible();
    }

    @Test
    void adminPageShowsAllAnnouncements() throws Exception {
        when(announcements.listAll()).thenReturn(List.of());

        mvc.perform(get("/admin/announcements")
                .with(user(account(Role.ADMINISTRATOR))))
            .andExpect(status().isOk())
            .andExpect(view().name("announcement/admin"))
            .andExpect(model().attributeExists("announcements"))
            .andExpect(model().attributeExists("command"));

        verify(announcements).listAll();
    }

    @Test
    void adminCreatesDraftUsingSingaporeTime() throws Exception {
        mvc.perform(post("/admin/announcements")
                .with(user(account(Role.ADMINISTRATOR)))
                .with(csrf())
                .param("title", "Library Maintenance")
                .param("content", "Scheduled air conditioning maintenance.")
                .param("validFrom", "2026-10-15T09:00")
                .param("validTo", "2026-10-16T18:00"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/announcements"));

        verify(announcements).createDraft(
            eq("Library Maintenance"),
            eq("Scheduled air conditioning maintenance."),
            eq(Instant.parse("2026-10-15T01:00:00Z")),
            eq(Instant.parse("2026-10-16T10:00:00Z")),
            eq(7L));
    }

    @Test
    void invalidFormReturnsAdminPageWithoutSaving() throws Exception {
        when(announcements.listAll()).thenReturn(List.of());

        mvc.perform(post("/admin/announcements")
                .with(user(account(Role.ADMINISTRATOR)))
                .with(csrf())
                .param("title", "")
                .param("content", "Maintenance notice")
                .param("validFrom", "2026-10-15T09:00"))
            .andExpect(status().isOk())
            .andExpect(view().name("announcement/admin"))
            .andExpect(model().hasErrors());

        verify(announcements, never()).createDraft(
            any(), any(), any(), any(), any());
    }

    @Test
    void adminPublishesAnnouncement() throws Exception {
        mvc.perform(post("/admin/announcements/5/publish")
                .with(user(account(Role.ADMINISTRATOR)))
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/announcements"));

        verify(announcements).publish(5L);
    }

    @Test
    void adminWithdrawsAnnouncement() throws Exception {
        mvc.perform(post("/admin/announcements/5/withdraw")
                .with(user(account(Role.ADMINISTRATOR)))
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/announcements"));

        verify(announcements).withdraw(5L);
    }

    @Test
    void invalidAnnouncementPeriodReturnsFormErrors() throws Exception {
        when(announcements.listAll()).thenReturn(List.of());

        mvc.perform(post("/admin/announcements")
                .with(user(account(Role.ADMINISTRATOR)))
                .with(csrf())
                .param("title", "Maintenance Notice")
                .param("content", "Scheduled maintenance.")
                .param("validFrom", "2026-10-20T09:00")
                .param("validTo", "2026-10-19T09:00"))
            .andExpect(status().isOk())
            .andExpect(view().name("announcement/admin"))
            .andExpect(model().hasErrors());

        verify(announcements, never()).createDraft(
            any(), any(), any(), any(), any());
    }

    private SmartFixUserDetails account(Role role) {
        return new SmartFixUserDetails(
            new UserAuthenticationData(
                7L,
                "alice",
                "hash",
                role,
                AccountStatus.ACTIVE,
                0L));
    }
}
