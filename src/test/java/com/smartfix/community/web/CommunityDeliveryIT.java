package com.smartfix.community.web;

import com.smartfix.audit.repository.AuditEntryRepository;
import com.smartfix.community.domain.*;
import com.smartfix.community.dto.*;
import com.smartfix.community.repository.*;
import com.smartfix.community.service.*;
import com.smartfix.notification.repository.NotificationRepository;
import com.smartfix.notification.service.NotificationService;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.CreateUserCommand;
import com.smartfix.user.service.UserService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real services, persistent delivery and the production security chain. No listener mocks. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:community-delivery-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "smartfix.bootstrap-admin.enabled=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommunityDeliveryIT {
    private static final String PASSWORD = "TestPassword9";
    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired CommunityQuestionRepository questions;
    @Autowired CommunityAnswerRepository answers;
    @Autowired CommunityReportRepository reports;
    @Autowired CommunityAnswerService answerService;
    @Autowired CommunityModerationService moderation;
    @Autowired NotificationRepository notifications;
    @Autowired NotificationService notificationService;
    @Autowired AuditEntryRepository audit;
    @Autowired PlatformTransactionManager transactions;
    Long alice, bob, admin;

    @BeforeAll void accounts() {
        alice = account("delivery.alice", Role.REQUESTER);
        bob = account("delivery.bob", Role.REQUESTER);
        admin = account("delivery.admin", Role.ADMINISTRATOR);
    }
    @BeforeEach void clean() {
        notifications.deleteAll(); audit.deleteAll(); reports.deleteAll();
        questions.findAll().forEach(q -> {
            if (q.getAcceptedAnswerId() != null) answerService.removeAcceptance(q.getId(), q.getAuthorId());
        });
        answers.deleteAll(); questions.deleteAll();
    }

    @Test void committedAnswerAndAcceptanceNotifyTheCorrectPeople() {
        var question = question();
        Long answer = answerService.post(question.getId(), body(), bob);
        var created = notificationService.getNotifications(alice);
        assertThat(created).hasSize(1);
        assertThat(created.get(0).getEventType()).isEqualTo("COMMUNITY_ANSWER_CREATED");
        assertThat(created.get(0).getTargetUrl()).isEqualTo("/community/questions/" + question.getId());
        assertThat(notificationService.getNotifications(bob)).isEmpty();
        answerService.accept(question.getId(), answer, alice);
        var accepted = notificationService.getNotifications(bob);
        assertThat(accepted).hasSize(1);
        assertThat(accepted.get(0).getEventType()).isEqualTo("COMMUNITY_ANSWER_ACCEPTED");
        assertThat(accepted.get(0).getReferenceId()).isEqualTo(question.getId());
        assertThatThrownBy(() -> answerService.accept(question.getId(), answer, alice))
                .isInstanceOf(com.smartfix.common.exception.BusinessConflictException.class);
        assertThat(notifications.count()).isEqualTo(2);
    }

    @Test void rolledBackAnswerOrAcceptanceDoesNotNotify() {
        var question = question();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            answerService.post(question.getId(), body(), bob);
            tx.setRollbackOnly();
        });
        assertThat(answers.count()).isZero();
        assertThat(notifications.count()).isZero();
        Long answer = answerService.post(question.getId(), body(), bob);
        notifications.deleteAll();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            answerService.accept(question.getId(), answer, alice);
            tx.setRollbackOnly();
        });
        assertThat(questions.findById(question.getId()).orElseThrow().getAcceptedAnswerId()).isNull();
        assertThat(notifications.count()).isZero();
    }

    @Test void ownReplyDoesNotNotifyAndSelfAcceptanceRemainsForbidden() {
        var question = question();
        Long answer = answerService.post(question.getId(), body(), alice);
        assertThat(notifications.count()).isZero();
        assertThatThrownBy(() -> answerService.accept(question.getId(), answer, alice))
                .hasMessage("You cannot accept your own answer.");
    }

    @Test void handledReportKeepsTheRestoreButtonAndRealAuditHistory() throws Exception {
        var question = question();
        var report = reports.saveAndFlush(CommunityReport.ofQuestion(bob, question.getId(),
                CommunityReportReason.SPAM, "Please review this content.", Instant.now()));
        var session = login("delivery.admin");
        mvc.perform(post("/admin/community/reports/{id}/resolve", report.getId())
                        .session(session).with(csrf()).param("decision", "ACTIONED")
                        .param("hideContent", "true").param("note", "Reviewed by administrator."))
                .andExpect(status().is3xxRedirection());
        assertThat(moderation.listReports(admin, ReportView.OPEN, 0, 10)).isEmpty();
        assertThat(moderation.listReports(admin, ReportView.HANDLED, 0, 10)).hasSize(1);
        String history = mvc.perform(get("/admin/community/reports").session(session)
                        .param("view", "HANDLED")).andExpect(status().isOk())
                .andExpect(content().string(containsString("/questions/" + question.getId() + "/restore")))
                .andExpect(content().string(containsString("Reviewed by administrator.")))
                .andReturn().getResponse().getContentAsString();
        assertThat(audit.findAll()).extracting(row -> row.getAction())
                .containsExactlyInAnyOrder("CONTENT_HIDDEN", "REPORT_RESOLVED");
        assertThat(audit.findAll()).allSatisfy(row -> {
            assertThat(row.getActorUserId()).isEqualTo(admin);
            assertThat(row.getOccurredAt()).isNotNull();
        });
        mvc.perform(post("/admin/community/questions/{id}/restore", question.getId())
                        .session(session).with(csrf()).param("view", "HANDLED").param("size", "10"))
                .andExpect(redirectedUrl("/admin/community/reports?view=HANDLED&page=0&size=10"));
        assertThat(questions.findById(question.getId()).orElseThrow().getStatus())
                .isEqualTo(CommunityContentStatus.VISIBLE);
        assertThat(audit.findAll()).extracting(row -> row.getAction()).contains("CONTENT_RESTORED");
        assertThat(moderation.restoreQuestion(question.getId(), admin)).isFalse();
        assertThat(audit.count()).isEqualTo(3);
        mvc.perform(get("/admin/community/audit").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Content restored")));
        export("handled-reports.html", history);
        export("audit.html", mvc.perform(get("/admin/community/audit").session(session))
                .andReturn().getResponse().getContentAsString());
    }

    @Test void moderationAndItsAuditRollBackTogether() {
        var question = question();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            moderation.hideQuestion(question.getId(), admin);
            assertThat(audit.count()).isEqualTo(1);
            tx.setRollbackOnly();
        });
        assertThat(questions.findById(question.getId()).orElseThrow().getStatus())
                .isEqualTo(CommunityContentStatus.VISIBLE);
        assertThat(audit.count()).isZero();
        assertThat(notifications.count()).isZero();
    }

    @Test void handledHistoryPagesKeepTheirViewAndAuditUsesBoundedPagination() throws Exception {
        for (int i = 0; i < 12; i++) {
            var question = question();
            var report = reports.saveAndFlush(CommunityReport.ofQuestion(bob, question.getId(),
                    CommunityReportReason.SPAM, null, Instant.now()));
            var decision = new ResolveReportCommand();
            decision.setDecision(CommunityReportStatus.DISMISSED);
            moderation.resolveReport(report.getId(), decision, admin);
        }
        var history = moderation.listReports(admin, ReportView.HANDLED, 0, 10);
        assertThat(history.getTotalElements()).isEqualTo(12);
        assertThat(history.getNumberOfElements()).isEqualTo(10);
        assertThat(moderation.listReports(admin, ReportView.HANDLED, 1, 10)).hasSize(2);
        mvc.perform(get("/admin/community/reports").session(login("delivery.admin"))
                        .param("view", "HANDLED").param("size", "10"))
                .andExpect(content().string(containsString("view=HANDLED&amp;page=1&amp;size=10")));
        mvc.perform(get("/admin/community/audit").session(login("delivery.admin"))
                        .param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pagination", org.hamcrest.Matchers.hasProperty("size",
                        org.hamcrest.Matchers.is(50))));
    }

    @Test void restoringAnAnswerDoesNotSilentlyRestoreItsAcceptance() {
        var question = question();
        Long answer = answerService.post(question.getId(), body(), bob);
        answerService.accept(question.getId(), answer, alice);
        moderation.hideAnswer(answer, admin);
        assertThat(questions.findById(question.getId()).orElseThrow().getAcceptedAnswerId()).isNull();
        moderation.restoreAnswer(answer, admin);
        assertThat(questions.findById(question.getId()).orElseThrow().getAcceptedAnswerId()).isNull();
        assertThat(audit.findAll()).extracting(row -> row.getTargetType()).containsOnly("ANSWER");
        assertThat(audit.count()).isEqualTo(2);
    }

    @Test void historyAndAuditRequireAdministratorAndNotificationReadRequiresOwnerAndCsrf()
            throws Exception {
        var session = login("delivery.bob");
        mvc.perform(get("/admin/community/reports").param("view", "HANDLED").session(session))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/community/audit").session(session)).andExpect(status().isForbidden());
        var question = question();
        answerService.post(question.getId(), body(), bob);
        Long id = notificationService.getNotifications(alice).get(0).getId();
        mvc.perform(post("/notifications/{id}/read", id).session(session).with(csrf()))
                .andExpect(status().isNotFound());
        var owner = login("delivery.alice");
        String html = mvc.perform(get("/notifications").session(owner)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Open discussion")))
                .andReturn().getResponse().getContentAsString();
        export("notifications.html", html);
        mvc.perform(post("/notifications/{id}/read", id).session(owner)).andExpect(status().isForbidden());
        mvc.perform(post("/notifications/{id}/read", id).session(owner).with(csrf()))
                .andExpect(redirectedUrl("/notifications"));
        assertThat(notificationService.getUnreadCount(alice)).isZero();
    }

    private Long account(String name, Role role) {
        var command = new CreateUserCommand();
        command.setUsername(name); command.setDisplayName(name);
        command.setPassword(PASSWORD); command.setRole(role);
        return users.createUser(command, null);
    }
    private CommunityQuestion question() {
        return questions.saveAndFlush(CommunityQuestion.ask(alice, "A sample campus question",
                "The classroom projector is not working. Can anyone help?", CommunityCategory.HARDWARE,
                Instant.now()));
    }
    private AnswerFormCommand body() {
        var form = new AnswerFormCommand(); form.setBody("Please check the cable and restart the projector.");
        return form;
    }
    private MockHttpSession login(String name) throws Exception {
        return (MockHttpSession) mvc.perform(post("/login").with(csrf())
                .param("username", name).param("password", PASSWORD)).andExpect(status().isFound())
                .andExpect(redirectedUrl("/"))
                .andReturn().getRequest().getSession(false);
    }
    private void export(String name, String html) throws java.io.IOException {
        String directory = System.getProperty("smartfix.ui.export");
        if (directory != null) {
            var path = java.nio.file.Path.of(directory); java.nio.file.Files.createDirectories(path);
            java.nio.file.Files.writeString(path.resolve(name), html);
        }
    }
}
