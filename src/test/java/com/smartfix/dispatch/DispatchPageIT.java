package com.smartfix.dispatch;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.dispatch.dto.AssignTechnicianCommand;
import com.smartfix.dispatch.dto.DispatchPageResponse;
import com.smartfix.dispatch.dto.ReassignTechnicianCommand;
import com.smartfix.dispatch.service.AssignmentService;
import com.smartfix.dispatch.service.DispatchPageService;
import com.smartfix.request.domain.*;
import com.smartfix.request.service.RequestReviewService;
import com.smartfix.technician.domain.AvailabilityStatus;
import com.smartfix.technician.dto.UpdateTechnicianProfileCommand;
import com.smartfix.technician.service.TechnicianDirectoryService;
import com.smartfix.user.domain.*;
import com.smartfix.user.dto.UserAuthenticationData;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.file.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises real rendering, security filters and committed B/C transactions, with no service mocks. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:dispatch-page-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DispatchPageIT {
    static final String TICKET = "SF-2026-000001";
    static final String BASE = "/admin/requests/" + TICKET;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AssignmentService assignments;
    @Autowired DispatchPageService pages;
    @Autowired TechnicianDirectoryService directory;
    @Autowired RequestReviewService reviews;

    @BeforeEach
    void fixtures() {
        for (String table : List.of("notifications", "assignments", "repair_records", "request_feedback", "request_status_history",
                "request_attachments", "work_orders", "maintenance_requests", "technician_skills",
                "technician_service_areas", "technician_profiles", "users", "locations")) {
            jdbc.update("DELETE FROM " + table);
        }
        var now = Timestamp.from(Instant.parse("2026-10-07T00:00:00Z"));
        for (long id = 100; id <= 104; id++) {
            String role = id == 100 ? "REQUESTER" : id <= 102 ? "TECHNICIAN" : "ADMINISTRATOR";
            jdbc.update("""
                    INSERT INTO users(id,username,display_name,password_hash,role,account_status,
                                      security_version,created_at,updated_at)
                    VALUES(?,?,?,?,?,'ACTIVE',0,?,?)
                    """, id, "dispatch.user" + id, id == 101 ? "Alex Tan" : id == 102 ? "Sam Lee" : "Test user " + id,
                    "synthetic-test-hash", role, now, now);
        }
        jdbc.update("INSERT INTO locations(id,location_code,display_name,active) VALUES(5,'LIB','Central Library',true)");
        for (long id : List.of(101L, 102L)) {
            var command = new UpdateTechnicianProfileCommand();
            command.setSkills(Set.of(MaintenanceCategory.ELECTRICAL, MaintenanceCategory.BUILDING));
            command.setServiceAreaIds(Set.of(5L));
            command.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
            directory.updateProfile(id, command);
        }
        jdbc.update("""
                INSERT INTO maintenance_requests(id,ticket_number,requester_id,location_id,title,description,
                                                 category,urgency_level,status,version,created_at,updated_at)
                VALUES(1,?,100,5,'Reading room lights not working','Synthetic request','ELECTRICAL','MEDIUM','SUBMITTED',0,?,?)
                """, TICKET, now, now);
        reviews.review(TICKET, UrgencyLevel.HIGH, 103L, false, null);
    }

    @Test
    void pageRendersRankedCandidatesReviewedPriorityAndCsrf() throws Exception {
        var result = mvc.perform(get(BASE + "/dispatch").with(account(103)))
                .andExpect(status().isOk()).andExpect(view().name("dispatch/assign"))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andReturn();
        var page = (DispatchPageResponse) result.getModelAndView().getModel().get("page");
        assertThat(page.priority()).isEqualTo(UrgencyLevel.HIGH);
        assertThat(page.canAssign()).isTrue();
        assertThat(page.candidates()).extracting(DispatchPageResponse.Candidate::userId).containsExactly(101L, 102L);
        assertThat(page.candidates()).allSatisfy(candidate -> {
            assertThat(candidate.username()).startsWith("dispatch.user");
            assertThat(candidate.openWorkOrders()).isZero();
        });
        snapshot("assign", result.getResponse().getContentAsString());
        mvc.perform(get("/admin/requests").with(account(103))).andExpect(status().isOk())
                .andExpect(content().string(containsString(BASE + "/dispatch")));
        mvc.perform(get("/requests/" + TICKET).with(account(103))).andExpect(status().isOk())
                .andExpect(content().string(containsString(BASE + "/dispatch")));
    }

    @Test
    void formsAssignReassignAndWithdrawUsingOnlyTheAuthenticatedActor() throws Exception {
        mvc.perform(post(BASE + "/assign").with(account(103)).with(csrf())
                        .param("technicianId", "101").param("actorUserId", "104").param("assignedByUserId", "104"))
                .andExpect(status().isFound()).andExpect(redirectedUrl(BASE + "/dispatch"))
                .andExpect(flash().attribute("successMessage", "Technician assigned."));
        var initial = assignments.findActiveAssignment(1L).orElseThrow();
        assertThat(initial.assignedByUserId()).isEqualTo(103);
        assertThat(jdbc.queryForObject("SELECT status FROM maintenance_requests WHERE id=1", String.class)).isEqualTo("ASSIGNED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM work_orders", Long.class)).isEqualTo(1);
        var html = mvc.perform(get(BASE + "/dispatch").with(account(103))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Current technician", "Reason for reassignment", "Withdraw assignment");
        snapshot("reassign", html);
        mvc.perform(get("/requests/" + TICKET).with(account(103))).andExpect(status().isOk())
                .andExpect(content().string(containsString(BASE + "/dispatch")));
        mvc.perform(post(BASE + "/reassign").with(account(103)).with(csrf())
                        .param("technicianId", "102").param("expectedAssignmentId", initial.id().toString())
                        .param("reason", "  Cover the afternoon shift  "))
                .andExpect(status().isFound()).andExpect(redirectedUrl(BASE + "/dispatch"));
        var replacement = assignments.findActiveAssignment(1L).orElseThrow();
        assertThat(replacement.technicianId()).isEqualTo(102);
        assertThat(replacement.reason()).isEqualTo("Cover the afternoon shift");
        mvc.perform(get("/requests/" + TICKET).with(account(101))).andExpect(status().isNotFound());
        mvc.perform(get("/requests/" + TICKET).with(account(102))).andExpect(status().isOk());
        mvc.perform(post(BASE + "/withdraw").with(account(103)).with(csrf())
                        .param("expectedAssignmentId", replacement.id().toString()).param("reason", "Review needed"))
                .andExpect(status().isFound()).andExpect(redirectedUrl(BASE + "/dispatch"));
        assertThat(assignments.findActiveAssignment(1L)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM maintenance_requests WHERE id=1", String.class)).isEqualTo("UNDER_REVIEW");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assignments", Long.class)).isEqualTo(2);
        mvc.perform(get("/requests/" + TICKET).with(account(102))).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "LONG"})
    void invalidReasonsLeaveAssignmentUntouchedAndPreserveInput(String reason) throws Exception {
        if (reason.equals("LONG")) reason = "x".repeat(501);
        var initial = assignments.assign(TICKET, new AssignTechnicianCommand(101L), 103L);
        for (String action : List.of("reassign", "withdraw")) {
            var result = mvc.perform(post(BASE + "/" + action).with(account(103)).with(csrf())
                            .param("technicianId", "102").param("expectedAssignmentId", initial.id().toString())
                            .param("reason", reason))
                    .andExpect(status().isBadRequest()).andExpect(view().name("dispatch/assign"))
                    .andExpect(model().attributeHasFieldErrors(action + "Command", "reason")).andReturn();
            assertThat(result.getResponse().getContentAsString()).contains(reason);
        }
        assertThat(assignments.findActiveAssignment(1L)).contains(initial);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "oops", ""})
    void invalidTechnicianIdsAreFormErrors(String id) throws Exception {
        mvc.perform(post(BASE + "/assign").with(account(103)).with(csrf()).param("technicianId", id))
                .andExpect(status().isBadRequest()).andExpect(model().attributeHasFieldErrors("assignCommand", "technicianId"));
        assertThat(assignments.findActiveAssignment(1L)).isEmpty();
    }

    @Test
    void staleFormShowsConflictPreservesReasonAndDoesNotReplaceNewAssignment() throws Exception {
        var old = assignments.assign(TICKET, new AssignTechnicianCommand(101L), 103L);
        var current = assignments.reassign(TICKET, new ReassignTechnicianCommand(102L, old.id(), "Shift coverage"), 104L);
        var result = mvc.perform(post(BASE + "/reassign").with(account(103)).with(csrf())
                        .param("technicianId", "101").param("expectedAssignmentId", old.id().toString())
                        .param("reason", "My pending change"))
                .andExpect(status().isConflict()).andExpect(model().attribute("conflict", true))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("My pending change", "Reload current details", "disabled=\"disabled\"");
        assertThat(assignments.findActiveAssignment(1L)).contains(current);
        snapshot("conflict", result.getResponse().getContentAsString());
    }

    @Test
    void technicianDisabledAfterPageLoadIsRejectedOnSubmit() throws Exception {
        mvc.perform(get(BASE + "/dispatch").with(account(103))).andExpect(status().isOk());
        jdbc.update("UPDATE users SET account_status='DISABLED' WHERE id=101");
        mvc.perform(post(BASE + "/assign").with(account(103)).with(csrf()).param("technicianId", "101"))
                .andExpect(status().isConflict()).andExpect(model().attribute("conflict", true));
        assertThat(assignments.findActiveAssignment(1L)).isEmpty();
    }

    @Test
    void emptyEngineerRosterOffersRealAccountCreation() throws Exception {
        jdbc.update("UPDATE users SET account_status='DISABLED' WHERE role='TECHNICIAN'");
        var empty = mvc.perform(get(BASE + "/dispatch").with(account(103))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(empty).contains("No active engineers yet", "Create engineer account").doesNotContain("name=\"technicianId\"");
        snapshot("empty", empty);
        jdbc.update("UPDATE locations SET active=false WHERE id=5");
        mvc.perform(get(BASE + "/dispatch").with(account(103))).andExpect(status().isOk())
                .andExpect(content().string(containsString("No active engineers yet")));
    }

    @Test
    void rosterIncludesUnconfiguredEngineerAndIgnoresSkillAreaAndLeavePreferences() throws Exception {
        jdbc.update("DELETE FROM technician_skills");
        jdbc.update("DELETE FROM technician_service_areas");
        jdbc.update("DELETE FROM technician_profiles");
        jdbc.update("UPDATE users SET password_change_required=true WHERE id=102");
        jdbc.update("UPDATE locations SET active=false WHERE id=5");
        var result = mvc.perform(get(BASE + "/dispatch").with(account(103))).andExpect(status().isOk()).andReturn();
        var page = (DispatchPageResponse) result.getModelAndView().getModel().get("page");
        assertThat(page.candidates()).extracting(DispatchPageResponse.Candidate::userId).containsExactly(101L,102L);
        mvc.perform(post(BASE + "/assign").with(account(103)).with(csrf()).param("technicianId","102"))
                .andExpect(status().isFound());
        assertThat(assignments.findActiveAssignment(1L).orElseThrow().technicianId()).isEqualTo(102);
    }

    @Test
    void unreviewedRequestOffersPriorityAndAssignmentTogether() throws Exception {
        jdbc.update("UPDATE maintenance_requests SET final_urgency_level=null WHERE id=1");
        var html = mvc.perform(get(BASE + "/dispatch").with(account(103))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("name=\"finalUrgencyLevel\"", "name=\"technicianId\"");
    }

    @Test
    void initialDispatchReviewsAndAssignsAtomicallyUsingTheAuthenticatedAdministrator() throws Exception {
        resetToSubmitted();
        var html = mvc.perform(get(BASE + "/dispatch").with(account(103)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Reviewed priority", "Reading room lights not working", "name=\"technicianId\"");
        snapshot("initial-dispatch", html);
        mvc.perform(post(BASE + "/assign").with(account(103)).with(csrf())
                .param("technicianId", "101").param("finalUrgencyLevel", "LOW")
                .param("actorUserId", "104")).andExpect(status().isFound());
        assertThat(requestState()).isEqualTo("ASSIGNED");
        assertThat(jdbc.queryForObject("SELECT final_urgency_level FROM maintenance_requests WHERE id=1", String.class)).isEqualTo("LOW");
        assertThat(jdbc.queryForObject("SELECT reviewed_by_user_id FROM maintenance_requests WHERE id=1", Long.class)).isEqualTo(103L);
        assertThat(assignments.findActiveAssignment(1L).orElseThrow().assignedByUserId()).isEqualTo(103L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM work_orders", Long.class)).isEqualTo(1L);
    }

    @Test
    void initialDispatchFailureDoesNotLeavePartialReviewAssignmentOrWorkOrder() throws Exception {
        resetToSubmitted();
        mvc.perform(post(BASE + "/assign").with(account(103)).with(csrf())
                .param("technicianId", "101")).andExpect(status().isBadRequest());
        jdbc.update("UPDATE users SET account_status='DISABLED' WHERE role='TECHNICIAN'");
        mvc.perform(post(BASE + "/assign").with(account(103)).with(csrf())
                .param("technicianId", "101").param("finalUrgencyLevel", "HIGH"))
                .andExpect(status().isConflict());
        assertThat(requestState()).isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("SELECT final_urgency_level FROM maintenance_requests WHERE id=1", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM request_status_history", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM work_orders", Long.class)).isZero();
        assertThat(assignments.findActiveAssignment(1L)).isEmpty();
    }

    @Test
    void completedRepairRequiresUserAcceptanceBeforeFinalAdministratorApproval() throws Exception {
        var orderId = assignedOrder();
        // Resources are enforced even if a different engineer calls the endpoints directly.
        mvc.perform(post("/workorders/" + orderId + "/accept").with(account(102)).with(csrf()))
                .andExpect(status().isNotFound());
        completeRepair(orderId);
        assertThat(requestState()).isEqualTo("RESOLVED");
        mvc.perform(post("/requests/" + TICKET + "/close").with(account(103)).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(post("/requests/" + TICKET + "/confirm").with(account(103)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/requests/" + TICKET + "/confirm").with(account(100)).with(csrf()))
                .andExpect(status().isFound());
        assertThat(requestState()).isEqualTo("CONFIRMED");
        var finalHtml = mvc.perform(get("/requests/" + TICKET).with(account(103)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(finalHtml).contains("Requester accepted the repair", "Approve &amp; close request", "Return for further repair", "name=\"_csrf\"");
        snapshot("final-review", finalHtml);
        mvc.perform(post("/requests/" + TICKET + "/close").with(account(100)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/requests/" + TICKET + "/close").with(account(103)).with(csrf()))
                .andExpect(status().isFound());
        assertThat(requestState()).isEqualTo("CLOSED");
        assertThat(jdbc.queryForObject("SELECT status FROM work_orders WHERE id=?", String.class, orderId)).isEqualTo("CLOSED");
    }

    @Test
    void finalReviewCanReturnWithReasonAndMustRepeatUserAcceptance() throws Exception {
        var orderId = assignedOrder();
        completeRepair(orderId);
        String endpoint = "/requests/" + TICKET;
        mvc.perform(post(endpoint + "/return-for-repair").with(account(103)).with(csrf()).param("comment", "Too soon"))
                .andExpect(status().isConflict());
        mvc.perform(post(endpoint + "/confirm").with(account(100)).with(csrf())).andExpect(status().isFound());
        mvc.perform(post(endpoint + "/return-for-repair").with(account(103)).with(csrf()).param("comment", " "))
                .andExpect(model().attributeHasFieldErrors("finalReview", "comment"));
        assertThat(requestState()).isEqualTo("CONFIRMED");
        for (long actor : List.of(100L, 101L)) {
            mvc.perform(post(endpoint + "/return-for-repair").with(account(actor)).with(csrf()).param("comment", "Forged review"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post(endpoint + "/return-for-repair").with(account(103)).param("comment", "Missing CSRF"))
                .andExpect(status().isForbidden());
        mvc.perform(post(endpoint + "/return-for-repair").with(account(103)).with(csrf()).param("comment", "Check the second light as well."))
                .andExpect(status().isFound());
        assertThat(requestState()).isEqualTo("REOPENED");
        assertThat(jdbc.queryForObject("SELECT confirmed_at FROM maintenance_requests WHERE id=1", Timestamp.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM request_status_history WHERE to_status='REOPENED' AND changed_by_user_id=103 AND comment='Check the second light as well.'", Long.class)).isEqualTo(1L);
        mvc.perform(post(endpoint + "/close").with(account(103)).with(csrf())).andExpect(status().isConflict());
        completeRepair(orderId);
        mvc.perform(post(endpoint + "/close").with(account(103)).with(csrf())).andExpect(status().isConflict());
        mvc.perform(post(endpoint + "/confirm").with(account(100)).with(csrf())).andExpect(status().isFound());
        mvc.perform(post(endpoint + "/close").with(account(103)).with(csrf())).andExpect(status().isFound());
        assertThat(requestState()).isEqualTo("CLOSED");
    }

    @Test
    void stageQueuesCoverAllPersistedStatesWithoutOverlapsAndKeepFiltersAcrossPages() throws Exception {
        var now = Timestamp.from(Instant.now());
        int id = 10;
        for (var state : RequestStatus.values()) {
            jdbc.update("INSERT INTO maintenance_requests(id,ticket_number,requester_id,location_id,title,description,category,urgency_level,status,version,created_at,updated_at) VALUES(?,?,100,5,'Stage fixture','Synthetic issue','ELECTRICAL','LOW',?,0,?,?)",
                    id, "SF-2026-%06d".formatted(id++), state.name(), now, now);
        }
        long total = 0;
        for (var stage : RequestStage.values()) {
            var response = mvc.perform(get("/admin/requests").with(account(103)).param("stage", stage.name()).param("size", "1"))
                    .andExpect(status().isOk()).andReturn();
            var page = (org.springframework.data.domain.Page<com.smartfix.request.dto.AdminRequestRow>) response.getModelAndView().getModel().get("pagination");
            assertThat(page.getContent()).allSatisfy(row -> assertThat(row.status().getStage()).isEqualTo(stage));
            assertThat(page.getTotalElements()).isEqualTo(stage.getStatuses().size() + (stage == RequestStage.TO_ASSIGN ? 1 : 0));
            total += page.getTotalElements();
            if (page.hasNext()) assertThat(response.getResponse().getContentAsString()).contains("stage=" + stage.name());
        }
        assertThat(total).isEqualTo(11);
        String html = mvc.perform(get("/admin").with(account(103))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int navigationStart = html.indexOf("<nav class=\"nav\"");
        var navigation = html.substring(navigationStart, html.indexOf("</nav>", navigationStart));
        var links = java.util.regex.Pattern.compile("<a\\b[^>]*href=\"([^\"]+)\"").matcher(navigation)
                .results().map(match -> match.group(1)).toList();
        assertThat(links).containsExactly(
                "/admin", "/admin/requests", "/admin/users", "/admin/facilities", "/admin/community/reports");
        snapshot("admin-overview", html);
        snapshot("request-queue", mvc.perform(get("/admin/requests").with(account(103)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void resetToSubmitted() {
        jdbc.update("DELETE FROM request_status_history");
        jdbc.update("UPDATE maintenance_requests SET status='SUBMITTED',final_urgency_level=null,reviewed_by_user_id=null,reviewed_at=null WHERE id=1");
    }

    private String requestState() {
        return jdbc.queryForObject("SELECT status FROM maintenance_requests WHERE id=1", String.class);
    }

    private Long assignedOrder() throws Exception {
        mvc.perform(post(BASE + "/assign").with(account(103)).with(csrf()).param("technicianId", "101"))
                .andExpect(status().isFound());
        return jdbc.queryForObject("SELECT id FROM work_orders WHERE request_id=1", Long.class);
    }

    private void completeRepair(Long id) throws Exception {
        mvc.perform(post("/workorders/" + id + "/accept").with(account(101)).with(csrf())).andExpect(status().isFound());
        mvc.perform(post("/workorders/" + id + "/records").with(account(101)).with(csrf())
                .param("diagnosis", "Synthetic fault").param("actionTaken", "Replaced the faulty light")
                .param("minutesSpent", "20")).andExpect(status().isFound());
        mvc.perform(post("/workorders/" + id + "/complete").with(account(101)).with(csrf())
                .param("resolutionNote", "Both lights tested and working.")).andExpect(status().isFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN_PROGRESS", "REOPENED", "RESOLVED", "CLOSED"})
    void pageActionsFollowRequestState(String state) throws Exception {
        assignments.assign(TICKET, new AssignTechnicianCommand(101L), 103L);
        jdbc.update("UPDATE maintenance_requests SET status=? WHERE id=1", state);
        var result = mvc.perform(get(BASE + "/dispatch").with(account(103))).andExpect(status().isOk()).andReturn();
        var page = (DispatchPageResponse) result.getModelAndView().getModel().get("page");
        assertThat(page.canAssign()).isFalse();
        assertThat(page.canWithdraw()).isFalse();
        assertThat(page.canReassign()).isEqualTo(state.equals("IN_PROGRESS") || state.equals("REOPENED"));
        if (page.canReassign()) {
            assertThat(page.candidates().stream().filter(c -> c.userId() == 101).findFirst().orElseThrow().selectable())
                    .isEqualTo(state.equals("REOPENED"));
        }
    }

    @Test
    void userContentIsEscapedInNamesTitleAndResubmittedReason() throws Exception {
        String payload = "<script>alert('xss')</script>";
        jdbc.update("UPDATE users SET display_name=? WHERE id=101", payload);
        jdbc.update("UPDATE maintenance_requests SET title=? WHERE id=1", payload);
        String html = mvc.perform(get(BASE + "/dispatch").with(account(103))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("&lt;script&gt;").doesNotContain(payload);
        var assigned = assignments.assign(TICKET, new AssignTechnicianCommand(101L), 103L);
        html = mvc.perform(post(BASE + "/reassign").with(account(103)).with(csrf())
                        .param("technicianId", "bad").param("expectedAssignmentId", assigned.id().toString()).param("reason", payload))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("&lt;script&gt;").doesNotContain(payload);
    }

    @Test
    void temporaryPasswordAdministratorCannotReadDispatchEvenThroughService() {
        jdbc.update("UPDATE users SET password_change_required=true WHERE id=103");
        assertThatThrownBy(() -> pages.describe(TICKET, 103L)).isInstanceOf(AccessDeniedException.class);
        assertThat(assignments.findActiveAssignment(1L)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(longs = {100, 101})
    void realPageAndWritesRejectNonAdministrators(long id) throws Exception {
        mvc.perform(get(BASE + "/dispatch").with(account(id))).andExpect(status().isForbidden());
        for (String action : List.of("assign", "reassign", "withdraw")) {
            mvc.perform(post(BASE + "/" + action).with(account(id)).with(csrf())).andExpect(status().isForbidden());
        }
        assertThatThrownBy(() -> pages.describe(TICKET, id)).isInstanceOf(AccessDeniedException.class);
        assertThat(assignments.findActiveAssignment(1L)).isEmpty();
    }

    @Test
    void missingCsrfAndDisabledSessionsCannotMutateAndMissingRequestsReturn404() throws Exception {
        for (String action : List.of("assign", "reassign", "withdraw")) {
            mvc.perform(post(BASE + "/" + action).with(account(103)).param("technicianId", "101"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/admin/requests/SF-2026-999999/dispatch").with(account(103))).andExpect(status().isNotFound());
        jdbc.update("UPDATE users SET account_status='DISABLED' WHERE id=103");
        mvc.perform(post(BASE + "/assign").with(account(103)).with(csrf()).param("technicianId", "101"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/login?expired"));
        assertThatThrownBy(() -> pages.describe(TICKET, 103L)).isInstanceOf(AccessDeniedException.class);
        assertThat(assignments.findActiveAssignment(1L)).isEmpty();
    }

    private RequestPostProcessor account(long id) {
        Role role = id == 100 ? Role.REQUESTER : id <= 102 ? Role.TECHNICIAN : Role.ADMINISTRATOR;
        return user(new SmartFixUserDetails(new UserAuthenticationData(
                id, "dispatch.user" + id, "synthetic-test-hash", role, AccountStatus.ACTIVE, 0L)));
    }

    private static void snapshot(String name, String html) throws Exception {
        Path directory = Path.of("target", "dispatch-preview");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".html"), html);
    }
}
