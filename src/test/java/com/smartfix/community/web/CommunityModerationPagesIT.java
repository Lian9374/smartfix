package com.smartfix.community.web;

import com.smartfix.community.domain.CommunityAnswer;
import com.smartfix.community.domain.CommunityCategory;
import com.smartfix.community.domain.CommunityContentStatus;
import com.smartfix.community.domain.CommunityQuestion;
import com.smartfix.community.domain.CommunityReport;
import com.smartfix.community.domain.CommunityReportReason;
import com.smartfix.community.domain.CommunityReportStatus;
import com.smartfix.community.repository.CommunityAnswerRepository;
import com.smartfix.community.repository.CommunityQuestionRepository;
import com.smartfix.community.repository.CommunityReportRepository;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.CreateUserCommand;
import com.smartfix.user.service.UserService;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reporting and moderation through the real pages: the queue an administrator works from,
 * and the report forms on the question thread.
 *
 * <h2>What only this layer can catch</h2>
 *
 * <p>A service test asserts a return value; a template error is invisible to it. The
 * queue's fragment calls, the per-report field ids, the governance buttons chosen by
 * target status, the flash attributes read back on the page they were flashed onto - each
 * is a runtime failure that no other test in this module reaches. So the assertions below
 * are about what the pages actually say, and about the redirect-and-flash round trip as
 * the browser performs it: the POST's own session is reused for the following GET, which
 * is the only way to prove the sentence a moderator is shown is the one the controller
 * flashed.</p>
 *
 * <h2>Authorisation, exercised and not assumed</h2>
 *
 * <p>These tests go through the production {@code SecurityFilterChain}: no test-scoped
 * chain stands in for it. Both halves of the rule are checked here - {@code /admin/**} is
 * refused to a signed-in requester and technician with a 403 from
 * {@code accessDeniedHandler}, and the five POSTs beside the queue are refused without a
 * CSRF token even to an administrator.</p>
 *
 * <h2>Where the database-level rules are</h2>
 *
 * <p>Not here. This class runs on H2, whose schema is built from the entities; the partial
 * unique indexes and the {@code CHECK} constraints that make "one report per reporter per
 * target" and "exactly one target" hold under concurrency exist only in {@code V19} and
 * are exercised against a real PostgreSQL server in
 * {@code CommunityModerationPostgresIT}.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:smartfix-community-moderation-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "smartfix.bootstrap-admin.enabled=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommunityModerationPagesIT {

    // Synthetic test-only credential, never an application default.
    private static final String PASSWORD = "TestPassword9";

    private static final Instant BASE = Instant.parse("2026-09-20T08:00:00Z");

    private static final String QUEUE = "/admin/community/reports";

    @Autowired private MockMvc mvc;
    @Autowired private UserService users;
    @Autowired private CommunityQuestionRepository questions;
    @Autowired private CommunityAnswerRepository answers;
    @Autowired private CommunityReportRepository reports;
    @Autowired private JdbcTemplate jdbc;

    private Long aliceId;
    private Long bobId;
    private Long adminId;

    private int seeded;

    @BeforeAll
    void accounts() {
        aliceId = createAccount("alice", Role.REQUESTER);
        bobId = createAccount("bob", Role.REQUESTER);
        adminId = createAccount("root.admin", Role.ADMINISTRATOR);
        // Created for its role and signed in by name; its id is not asserted on.
        createAccount("tech", Role.TECHNICIAN);
    }

    // ------------------------------------------------------------ the queue

    @Test
    void theQueueShowsEachReportWithTheContentItIsAbout() throws Exception {
        CommunityQuestion question = seed(aliceId, "Queue rendering sample",
                "The projector in seminar room three shows no picture at all.");
        CommunityAnswer answer = answers.saveAndFlush(CommunityAnswer.post(question.getId(), bobId,
                "Have you checked the power cable behind the desk?", BASE.plusSeconds(60)));
        Long questionReport = report(CommunityReport.ofQuestion(bobId, question.getId(),
                CommunityReportReason.SPAM, "Posted three times today.", BASE.plusSeconds(120)));
        Long answerReport = report(CommunityReport.ofAnswer(aliceId, answer.getId(),
                CommunityReportReason.OFF_TOPIC, null, BASE.plusSeconds(180)));

        mvc.perform(queuePage(login("root.admin")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Reported content")))
                // The report's own facts, each one a value the backend holds.
                .andExpect(content().string(containsString("Report #" + questionReport)))
                .andExpect(content().string(containsString("SPAM")))
                .andExpect(content().string(containsString("OFF_TOPIC")))
                .andExpect(content().string(containsString("reported by Account #" + bobId)))
                .andExpect(content().string(containsString("reported by Account #" + aliceId)))
                // The text the decision is about, in full - both kinds of target.
                .andExpect(content().string(containsString(
                        "The projector in seminar room three shows no picture at all.")))
                .andExpect(content().string(containsString(
                        "Have you checked the power cable behind the desk?")))
                .andExpect(content().string(containsString(
                        "The reporter added: Posted three times today.")))
                // Which kind each row is about, and who wrote the reported content.
                .andExpect(content().string(containsString("Reported question")))
                .andExpect(content().string(containsString("Reported answer")))
                .andExpect(content().string(containsString("written by Account #" + aliceId)))
                .andExpect(content().string(containsString("written by Account #" + bobId)))
                // The thread is reachable, so it is offered.
                .andExpect(content().string(containsString("Open the thread")))
                .andExpect(content().string(containsString(
                        "href=\"/community/questions/" + question.getId() + "\"")))
                // One decision form per report, aimed at that report by path.
                .andExpect(content().string(containsString(
                        "action=\"/admin/community/reports/" + questionReport + "/resolve\"")))
                .andExpect(content().string(containsString(
                        "action=\"/admin/community/reports/" + answerReport + "/resolve\"")))
                .andExpect(content().string(containsString("value=\"ACTIONED\"")))
                .andExpect(content().string(containsString("value=\"DISMISSED\"")))
                .andExpect(content().string(containsString("name=\"hideContent\"")))
                // The governance buttons for a visible target: hide, and not restore.
                .andExpect(content().string(containsString(
                        "action=\"/admin/community/questions/" + question.getId() + "/hide\"")))
                .andExpect(content().string(containsString(
                        "action=\"/admin/community/answers/" + answer.getId() + "/hide\"")))
                .andExpect(content().string(not(containsString(
                        "action=\"/admin/community/questions/" + question.getId() + "/restore\""))))
                .andExpect(content().string(not(containsString(
                        "action=\"/admin/community/answers/" + answer.getId() + "/restore\""))));
    }

    /**
     * Everything a person typed - the reported body and the reporter's note - arrives as
     * text. The escaped form is asserted present, not merely the raw form absent: a page
     * that dropped the field entirely would satisfy the weaker check.
     */
    @Test
    void theQueueRendersReportedWordsAsTextAndNeverAsMarkup() throws Exception {
        String injected = "<script>alert('queue')</script>";
        CommunityQuestion question = seed(aliceId, "Queue escaping sample",
                "A question body carrying " + injected + " inside it.");
        report(CommunityReport.ofQuestion(bobId, question.getId(),
                CommunityReportReason.ABUSIVE, "The note carries " + injected + " too.",
                BASE.plusSeconds(240)));

        mvc.perform(queuePage(login("root.admin")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString(injected))));
    }

    /**
     * A moderator deciding about hidden content has to be able to read it: the public
     * thread answers 404 once a question is hidden, so the queue is the only place the
     * text is still on screen. The action offered follows from the status as well - there
     * is nothing to hide, so the row offers only the way back.
     */
    @Test
    void aHiddenTargetIsStillReadableOnTheQueueAndOffersOnlyRestore() throws Exception {
        CommunityQuestion question = seed(aliceId, "Hidden target sample",
                "A question that will be hidden while a report about it is still open.");
        report(CommunityReport.ofQuestion(bobId, question.getId(),
                CommunityReportReason.ABUSIVE, null, BASE.plusSeconds(300)));

        mvc.perform(post("/admin/community/questions/" + question.getId() + "/hide")
                        .with(csrf()).session(login("root.admin")))
                .andExpect(status().isFound());

        mvc.perform(queuePage(login("root.admin")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "A question that will be hidden while a report about it is still open.")))
                .andExpect(content().string(containsString("HIDDEN")))
                .andExpect(content().string(containsString(
                        "action=\"/admin/community/questions/" + question.getId() + "/restore\"")))
                .andExpect(content().string(not(containsString(
                        "action=\"/admin/community/questions/" + question.getId() + "/hide\""))))
                // The thread no longer renders for anybody but its author, so no link
                // to it is offered. A destination that leads to a 404 is worse than none.
                .andExpect(content().string(not(containsString(
                        "href=\"/community/questions/" + question.getId() + "\""))));
    }

    /**
     * Withdrawn content is not a moderator's to restore, and the row says so rather than
     * leaving an absent button to be read as a broken page.
     */
    @Test
    void aWithdrawnTargetIsExplainedRatherThanGivenAnAction() throws Exception {
        CommunityQuestion question = seed(aliceId, "Withdrawn target sample",
                "A question its author took back while a report about it was open.");
        CommunityAnswer answer = answers.saveAndFlush(CommunityAnswer.post(question.getId(), bobId,
                "An answer that will be withdrawn by its own author.", BASE.plusSeconds(60)));
        withdrawAnswer(answer.getId());

        report(CommunityReport.ofAnswer(aliceId, answer.getId(),
                CommunityReportReason.OTHER, null, BASE.plusSeconds(360)));

        mvc.perform(queuePage(login("root.admin")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "An answer that will be withdrawn by its own author.")))
                .andExpect(content().string(containsString("Its author withdrew this content.")))
                .andExpect(content().string(not(containsString(
                        "action=\"/admin/community/answers/" + answer.getId() + "/hide\""))))
                .andExpect(content().string(not(containsString(
                        "action=\"/admin/community/answers/" + answer.getId() + "/restore\""))));
    }

    // ------------------------------------------------------ authorisation

    @Test
    void anOrdinaryAccountIsRefusedEveryModerationRoute() throws Exception {
        CommunityQuestion question = seed(aliceId, "Unauthorised moderation sample",
                "A question a requester will try to moderate.");
        CommunityAnswer answer = answers.saveAndFlush(CommunityAnswer.post(question.getId(), bobId,
                "An answer a requester will try to moderate.", BASE.plusSeconds(60)));
        Long reportId = report(CommunityReport.ofQuestion(bobId, question.getId(),
                CommunityReportReason.SPAM, null, BASE.plusSeconds(420)));

        for (String username : new String[] {"alice", "tech"}) {
            MockHttpSession session = login(username);

            mvc.perform(queuePage(session))
                    .andExpect(status().isForbidden());

            mvc.perform(post("/admin/community/reports/" + reportId + "/resolve")
                            .param("decision", "ACTIONED").with(csrf()).session(session))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/admin/community/questions/" + question.getId() + "/hide")
                            .with(csrf()).session(session))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/admin/community/questions/" + question.getId() + "/restore")
                            .with(csrf()).session(session))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/admin/community/answers/" + answer.getId() + "/hide")
                            .with(csrf()).session(session))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/admin/community/answers/" + answer.getId() + "/restore")
                            .with(csrf()).session(session))
                    .andExpect(status().isForbidden());
        }

        // A 403 that still wrote something would be the worst of both, so the state is
        // checked as well as the status code.
        assertThat(statusOfQuestion(question.getId())).isEqualTo(CommunityContentStatus.VISIBLE);
        assertThat(statusOfAnswer(answer.getId())).isEqualTo(CommunityContentStatus.VISIBLE);
        assertThat(reports.findById(reportId).orElseThrow().getStatus())
                .isEqualTo(CommunityReportStatus.OPEN);
    }

    @Test
    void aSignedOutVisitorIsSentToTheLoginPageRatherThanTheQueue() throws Exception {
        mvc.perform(get(QUEUE))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("/login")));
    }

    @Test
    void aModerationPostWithoutACsrfTokenIsRefused() throws Exception {
        CommunityQuestion question = seed(aliceId, "Csrf moderation sample",
                "A question whose hide route will be posted to without a token.");

        mvc.perform(post("/admin/community/questions/" + question.getId() + "/hide")
                        .session(login("root.admin")))
                .andExpect(status().isForbidden());

        assertThat(statusOfQuestion(question.getId())).isEqualTo(CommunityContentStatus.VISIBLE);
    }

    /**
     * The rail may only carry a link to a route that exists and that this reader may use,
     * so the queue appears for an administrator and for nobody else.
     */
    @Test
    void theRailOffersTheQueueToAnAdministratorAndToNobodyElse() throws Exception {
        mvc.perform(queuePage(login("root.admin")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Reported Content")))
                .andExpect(content().string(containsString("href=\"/admin/community/reports\"")))
                .andExpect(content().string(containsString("aria-current=\"page\"")));

        mvc.perform(get("/community").session(login("bob")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/admin/community/reports"))));
    }

    // ------------------------------------------------------------ reporting

    @Test
    void reportingAQuestionIsAcknowledgedOnTheThreadItWasFiledFrom() throws Exception {
        CommunityQuestion question = seed(aliceId, "Reported question sample",
                "A question a reader will report and be thanked for on the same page.");

        MvcResult reported = mvc.perform(post("/community/questions/" + question.getId() + "/reports")
                        .param("reason", "SPAM").param("detail", "  The same text twice.  ")
                        .with(csrf()).session(login("bob")))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/community/questions/" + question.getId()))
                .andReturn();

        // Back on the page it was filed from, and told so. The session the POST produced
        // is the one that carries the flash, which is what makes this the browser's round
        // trip rather than a check of the redirect target in isolation.
        mvc.perform(get("/community/questions/" + question.getId()).session(after(reported)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Thank you. A moderator will review this question.")));

        CommunityReport stored = reports.findAll().stream()
                .filter(row -> question.getId().equals(row.getQuestionId()))
                .findFirst().orElseThrow();
        assertThat(stored.getReporterId()).isEqualTo(bobId);
        assertThat(stored.getAnswerId()).isNull();
        assertThat(stored.getReason()).isEqualTo(CommunityReportReason.SPAM);
        assertThat(stored.getDetail()).isEqualTo("The same text twice.");
        assertThat(stored.getStatus()).isEqualTo(CommunityReportStatus.OPEN);
    }

    @Test
    void aSecondReportOfTheSameThingIsRefusedWithASentence() throws Exception {
        CommunityQuestion question = seed(aliceId, "Double report sample",
                "A question one reader will report twice.");

        mvc.perform(post("/community/questions/" + question.getId() + "/reports")
                        .param("reason", "SPAM").with(csrf()).session(login("bob")))
                .andExpect(status().isFound());

        MvcResult second = mvc.perform(post("/community/questions/" + question.getId() + "/reports")
                        .param("reason", "ABUSIVE").with(csrf()).session(login("bob")))
                .andExpect(status().isFound())
                .andReturn();

        mvc.perform(get("/community/questions/" + question.getId()).session(after(second)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("You have already reported this.")));

        // One row, and the reason the first report gave rather than the second.
        assertThat(reports.findAll().stream()
                .filter(row -> question.getId().equals(row.getQuestionId()))
                .map(CommunityReport::getReason)
                .toList())
                .containsExactly(CommunityReportReason.SPAM);
    }

    @Test
    void aReportWithoutAReasonIsRefusedRatherThanStored() throws Exception {
        CommunityQuestion question = seed(aliceId, "Reasonless report sample",
                "A question reported by a hand-built request with no reason.");

        MvcResult refused = mvc.perform(post("/community/questions/" + question.getId() + "/reports")
                        .with(csrf()).session(login("bob")))
                .andExpect(status().isFound())
                .andReturn();

        mvc.perform(get("/community/questions/" + question.getId()).session(after(refused)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Choose a reason for the report, then submit it again.")));

        assertThat(reports.findAll().stream()
                .filter(row -> question.getId().equals(row.getQuestionId()))
                .toList())
                .isEmpty();
    }

    @Test
    void reportingAnAnswerReturnsToTheThreadItBelongsTo() throws Exception {
        CommunityQuestion question = seed(aliceId, "Reported answer sample",
                "A question whose answer will be reported by a reader.");
        CommunityAnswer answer = answers.saveAndFlush(CommunityAnswer.post(question.getId(), bobId,
                "An answer another reader will report to a moderator.", BASE.plusSeconds(60)));

        MvcResult reported = mvc.perform(post("/community/answers/" + answer.getId() + "/reports")
                        .param("reason", "OFF_TOPIC").with(csrf()).session(login("tech")))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/community/questions/" + question.getId()))
                .andReturn();

        mvc.perform(get("/community/questions/" + question.getId()).session(after(reported)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Thank you. A moderator will review this answer.")));

        CommunityReport stored = reports.findAll().stream()
                .filter(row -> answer.getId().equals(row.getAnswerId()))
                .findFirst().orElseThrow();
        // The report names the answer and not the thread, which is the constraint V19
        // enforces on every row.
        assertThat(stored.getQuestionId()).isNull();
        assertThat(stored.getStatus()).isEqualTo(CommunityReportStatus.OPEN);
    }

    /**
     * Neither address a report can be filed at accepts one when the content behind it is
     * not public: the question route answers 404, and nothing is written.
     *
     * <p>A 404 rather than a conflict, for the reason every refusal in this module gives
     * one - a conflict would confirm that the id names something, which a reader has no
     * business learning from a report form.</p>
     */
    @Test
    void contentNobodyCanReadCannotBeReported() throws Exception {
        CommunityQuestion question = seed(aliceId, "Unreadable report sample",
                "A question withdrawn before anybody could report it.");
        CommunityAnswer answer = answers.saveAndFlush(CommunityAnswer.post(question.getId(), bobId,
                "An answer on a question that will be withdrawn.", BASE.plusSeconds(60)));
        jdbc.update("UPDATE community_questions SET status = ? WHERE id = ?",
                "WITHDRAWN", question.getId());

        mvc.perform(post("/community/questions/" + question.getId() + "/reports")
                        .param("reason", "SPAM").with(csrf()).session(login("bob")))
                .andExpect(status().isNotFound());

        // The answer is still VISIBLE, but its question is not, so the answer route refuses
        // it too: a report about content nobody can reach is not one a moderator can act on.
        mvc.perform(post("/community/answers/" + answer.getId() + "/reports")
                        .param("reason", "SPAM").with(csrf()).session(login("bob")))
                .andExpect(status().isNotFound());

        assertThat(reports.findAll().stream()
                .filter(row -> question.getId().equals(row.getQuestionId())
                        || answer.getId().equals(row.getAnswerId()))
                .toList())
                .isEmpty();
    }

    /**
     * The form is on content the reader did not write, and only there.
     *
     * <p>Alice viewing her own question and Bob's answer sees one form, for the answer.
     * Bob viewing the same page sees one form, for the question. That is the whole rule,
     * checked from both sides on one page rather than by trusting the render.</p>
     */
    @Test
    void theReportFormIsOfferedOnlyOnContentTheReaderDidNotWrite() throws Exception {
        CommunityQuestion question = seed(aliceId, "Self report sample",
                "A question its author must not be offered a report form for.");
        CommunityAnswer answer = answers.saveAndFlush(CommunityAnswer.post(question.getId(), bobId,
                "An answer its author must not be offered a report form for.", BASE.plusSeconds(60)));
        String path = "/community/questions/" + question.getId();

        String asAuthorOfQuestion = mvc.perform(get(path).session(login("alice")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(asAuthorOfQuestion).doesNotContain("Report this question");
        assertThat(asAuthorOfQuestion).contains("Report this answer");

        String asAuthorOfAnswer = mvc.perform(get(path).session(login("bob")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(asAuthorOfAnswer).contains("Report this question");
        assertThat(asAuthorOfAnswer).doesNotContain("Report this answer");

        String asStranger = mvc.perform(get(path).session(login("tech")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(asStranger).contains("Report this question");
        assertThat(asStranger).contains("Report this answer");

        // Both forms post to the routes the service guards, and each one names its target
        // in the path rather than in a field that could be edited.
        assertThat(asStranger)
                .contains("action=\"/community/questions/" + question.getId() + "/reports\"")
                .contains("action=\"/community/answers/" + answer.getId() + "/reports\"");
    }

    /**
     * A withdrawn question is not on the board at all for anybody but its author, so the
     * gate that matters here is the answer's own: the thread still renders, and the
     * withdrawn answer in it offers no report form while the visible question above it
     * still does.
     *
     * <p>The two gates are separated on purpose. Checking only the question-level one
     * would pass even if the per-answer condition were missing entirely.</p>
     */
    @Test
    void aWithdrawnAnswerOffersNoReportFormWhileItsThreadStillDoes() throws Exception {
        CommunityQuestion question = seed(aliceId, "Closed answer sample",
                "A question whose answer is withdrawn while the question stays visible.");
        CommunityAnswer answer = answers.saveAndFlush(CommunityAnswer.post(question.getId(), bobId,
                "An answer its author will take back before anybody reports it.",
                BASE.plusSeconds(60)));
        withdrawAnswer(answer.getId());

        mvc.perform(get("/community/questions/" + question.getId()).session(login("tech")))
                .andExpect(status().isOk())
                // The question is public and the reader did not write it, so its form is there.
                .andExpect(content().string(containsString("Report this question")))
                // The answer is not public, so it is not a reportable thing.
                .andExpect(content().string(not(containsString("Report this answer"))));
    }

    @Test
    void aWithdrawnQuestionIsNotReachableToReportAtAll() throws Exception {
        CommunityQuestion question = seed(aliceId, "Withdrawn question sample",
                "A question withdrawn before a reader could report it.");
        jdbc.update("UPDATE community_questions SET status = ? WHERE id = ?",
                "WITHDRAWN", question.getId());

        // Nobody but the author can read it, and a moderator is not its author.
        mvc.perform(get("/community/questions/" + question.getId()).session(login("bob")))
                .andExpect(status().isNotFound());

        // Its author can still read the thread, and is offered no form on it - she wrote it.
        mvc.perform(get("/community/questions/" + question.getId()).session(login("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Report this question"))));
    }

    /**
     * The control offers exactly the reasons the server binds. A value the enumeration does
     * not have would be refused on submit, and one it has but the page omitted would be
     * unreachable.
     */
    @Test
    void theReasonControlOffersExactlyTheReasonsTheServerAccepts() throws Exception {
        CommunityQuestion question = seed(aliceId, "Reason list sample",
                "A question used to check the reason control on the report form.");

        String body = mvc.perform(get("/community/questions/" + question.getId())
                        .session(login("bob")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (CommunityReportReason reason : CommunityReportReason.values()) {
            assertThat(body)
                    .as("the report form must offer %s", reason)
                    .contains("value=\"" + reason.name() + "\"");
        }
        // The limit the server enforces is on the control too.
        assertThat(body).contains("maxlength=\"500\"");
        assertThat(body).contains("id=\"report-question-reason\"");
    }

    // ------------------------------------------------------------ decisions

    @Test
    void upholdingAReportWithHideTakesTheContentDownAndReturnsToTheQueue() throws Exception {
        CommunityQuestion question = seed(aliceId, "Decision sample",
                "A question a moderator will uphold a report about and hide.");
        Long reportId = report(CommunityReport.ofQuestion(bobId, question.getId(),
                CommunityReportReason.SPAM, null, BASE.plusSeconds(480)));

        MvcResult decided = mvc.perform(post("/admin/community/reports/" + reportId + "/resolve")
                        .param("decision", "ACTIONED").param("note", "  Removed.  ")
                        .param("hideContent", "true")
                        .with(csrf()).session(login("root.admin")))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(QUEUE))
                .andReturn();

        mvc.perform(queuePage(after(decided)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "The report was resolved and the content was hidden.")));

        assertThat(statusOfQuestion(question.getId())).isEqualTo(CommunityContentStatus.HIDDEN);

        CommunityReport stored = reports.findById(reportId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(CommunityReportStatus.ACTIONED);
        assertThat(stored.getHandledByUserId()).isEqualTo(adminId);
        assertThat(stored.getHandledAt()).isNotNull();
        assertThat(stored.getResolutionNote()).isEqualTo("Removed.");
        // The decision moved the report out of the queue, so the queue no longer holds it.
        assertThat(mvc.perform(queuePage(login("root.admin")))
                .andReturn().getResponse().getContentAsString())
                .doesNotContain("Report #" + reportId);
    }

    /**
     * The second submission of a double-clicked decision, through the page a moderator
     * actually double-clicks. It must change nothing, it must not hide anything, and it
     * must not describe the first moderator's decision as this one's work.
     */
    @Test
    void aDecisionSomebodyElseAlreadyTookChangesNothingAndSaysSo() throws Exception {
        CommunityQuestion question = seed(aliceId, "Contested decision sample",
                "A question two moderators will decide about at the same time.");
        Long reportId = report(CommunityReport.ofQuestion(bobId, question.getId(),
                CommunityReportReason.SPAM, null, BASE.plusSeconds(540)));

        mvc.perform(post("/admin/community/reports/" + reportId + "/resolve")
                        .param("decision", "DISMISSED").param("note", "Not against the rules.")
                        .with(csrf()).session(login("root.admin")))
                .andExpect(status().isFound());

        MvcResult losing = mvc.perform(post("/admin/community/reports/" + reportId + "/resolve")
                        .param("decision", "ACTIONED").param("hideContent", "true")
                        .with(csrf()).session(login("root.admin")))
                .andExpect(status().isFound())
                .andReturn();

        mvc.perform(queuePage(after(losing)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Another moderator had already handled this report, so nothing was "
                                + "changed.")));

        // The first decision stands, note and all, and the losing submission did not hide
        // the content on the strength of a decision it did not make.
        CommunityReport stored = reports.findById(reportId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(CommunityReportStatus.DISMISSED);
        assertThat(stored.getResolutionNote()).isEqualTo("Not against the rules.");
        assertThat(statusOfQuestion(question.getId())).isEqualTo(CommunityContentStatus.VISIBLE);
    }

    @Test
    void aSubmissionWithNoDecisionIsRefusedByTheForm() throws Exception {
        CommunityQuestion question = seed(aliceId, "Undecided submission sample",
                "A question whose report will be submitted with no decision chosen.");
        Long reportId = report(CommunityReport.ofQuestion(bobId, question.getId(),
                CommunityReportReason.OTHER, null, BASE.plusSeconds(600)));

        MvcResult refused = mvc.perform(post("/admin/community/reports/" + reportId + "/resolve")
                        .param("note", "no decision")
                        .with(csrf()).session(login("root.admin")))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(QUEUE))
                .andReturn();

        mvc.perform(queuePage(after(refused)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Choose whether the report is upheld or dismissed")));

        assertThat(reports.findById(reportId).orElseThrow().getStatus())
                .isEqualTo(CommunityReportStatus.OPEN);
    }

    @Test
    void theGovernanceButtonsHideAndRestoreAQuestionThroughThePage() throws Exception {
        CommunityQuestion question = seed(aliceId, "Governance sample",
                "A question a moderator will hide and then bring back.");

        MvcResult hidden = mvc.perform(post("/admin/community/questions/" + question.getId() + "/hide")
                        .with(csrf()).session(login("root.admin")))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(QUEUE))
                .andReturn();
        mvc.perform(queuePage(after(hidden)))
                .andExpect(content().string(containsString(
                        "The question is no longer publicly visible.")));
        assertThat(statusOfQuestion(question.getId())).isEqualTo(CommunityContentStatus.HIDDEN);

        // Nobody but the author can read a hidden thread, and the moderator is not its
        // author, so this is what "taken down" means from the page's point of view.
        mvc.perform(get("/community/questions/" + question.getId()).session(login("root.admin")))
                .andExpect(status().isNotFound());

        MvcResult restored = mvc.perform(
                        post("/admin/community/questions/" + question.getId() + "/restore")
                                .with(csrf()).session(login("root.admin")))
                .andExpect(status().isFound())
                .andReturn();
        mvc.perform(queuePage(after(restored)))
                .andExpect(content().string(containsString(
                        "The question is publicly visible again.")));
        assertThat(statusOfQuestion(question.getId())).isEqualTo(CommunityContentStatus.VISIBLE);
    }

    @Test
    void hidingWhatIsAlreadyHiddenSaysNothingChanged() throws Exception {
        CommunityQuestion question = seed(aliceId, "Repeat hide sample",
                "A question a moderator will try to hide twice.");

        mvc.perform(post("/admin/community/questions/" + question.getId() + "/hide")
                        .with(csrf()).session(login("root.admin")))
                .andExpect(status().isFound());

        MvcResult second = mvc.perform(post("/admin/community/questions/" + question.getId() + "/hide")
                        .with(csrf()).session(login("root.admin")))
                .andExpect(status().isFound())
                .andReturn();

        mvc.perform(queuePage(after(second)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "The question was already not publicly visible, so nothing changed.")));
    }

    // -------------------------------------------------------------- helpers

    /**
     * A read of the whole queue, for a moderator who is already signed in.
     *
     * <p>The size asked for is the largest the product allows, because this class shares one
     * database across its tests and the queue is ordered oldest first: a test that assumed
     * its own report was on the first page of ten would be asserting on whichever other test
     * happened to run before it. What the page boundaries are is checked against a database
     * holding nothing else, in {@code CommunityModerationQueuePagingIT}.</p>
     */
    private MockHttpServletRequestBuilder queuePage(MockHttpSession session) {
        return get(QUEUE).param("size", "50").session(session);
    }

    /** The session carrying a POST's flash attributes, for the GET that follows it. */
    private static MockHttpSession after(MvcResult result) {
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session)
                .as("a flashed response must leave a session for the next request to read")
                .isNotNull();
        return session;
    }

    private CommunityContentStatus statusOfQuestion(Long questionId) {
        return questions.findById(questionId).orElseThrow().getStatus();
    }

    private CommunityContentStatus statusOfAnswer(Long answerId) {
        return answers.findById(answerId).orElseThrow().getStatus();
    }

    /**
     * A withdrawal written straight to the column.
     *
     * <p>The author's own routes are not what these tests are about, and a withdrawal made
     * through one would put an extra redirect and flash between the seeding and the request
     * under test. The column is the state; how it got there does not matter here.</p>
     */
    private void withdrawAnswer(Long answerId) {
        jdbc.update("UPDATE community_answers SET status = ? WHERE id = ?", "WITHDRAWN", answerId);
    }

    /** Stores a report and returns its id. */
    private Long report(CommunityReport report) {
        return reports.saveAndFlush(report).getId();
    }

    /** Seeds a visible question with a timestamp later than every question before it. */
    private CommunityQuestion seed(Long authorId, String title, String body) {
        return questions.saveAndFlush(CommunityQuestion.ask(authorId, title, body,
                CommunityCategory.OTHER, BASE.plusSeconds(60L * (++seeded))));
    }

    private Long createAccount(String username, Role role) {
        CreateUserCommand command = new CreateUserCommand();
        command.setUsername(username);
        command.setDisplayName(username + " (test)");
        command.setPassword(PASSWORD);
        command.setRole(role);
        return users.createUser(command, null);
    }

    private MockHttpSession login(String username) throws Exception {
        // A failed sign-in redirects to /login?error, so the expected redirect is asserted
        // here: a session that never authenticated must not be mistaken for one that did
        // and quietly turn every assertion below into a redirect check.
        return (MockHttpSession) mvc.perform(post("/login").with(csrf())
                        .param("username", username).param("password", PASSWORD))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/"))
                .andReturn().getRequest().getSession(false);
    }
}
