package com.smartfix.community.web;

import com.smartfix.community.domain.CommunityCategory;
import com.smartfix.community.domain.CommunityQuestion;
import com.smartfix.community.domain.CommunityReport;
import com.smartfix.community.domain.CommunityReportReason;
import com.smartfix.community.repository.CommunityQuestionRepository;
import com.smartfix.community.repository.CommunityReportRepository;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.CreateUserCommand;
import com.smartfix.user.service.UserService;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The moderation queue's own list behaviour - its page boundaries and its empty state -
 * against a database that holds nothing but what each test puts there.
 *
 * <h2>Why this is not in {@code CommunityModerationPagesIT}</h2>
 *
 * <p>Both states are unreachable in a shared database. The queue is ordered oldest first
 * and served ten to a page, so once any other test has filed a report, "ten of the twelve
 * I just created are on page 0" and "no reports are waiting" are both statements about a
 * table only this class controls. The precedent is {@code CommunityEmptyBoardIT}, which
 * exists for the same reason and says so.</p>
 *
 * <h2>What is asserted, and what is not</h2>
 *
 * <p>That the size a reader asked for travels into the pager rather than resetting to the
 * default, that the oldest reports are the ones on the first page, that no control offers a
 * page that does not exist, and that the empty queue says it is empty rather than blaming
 * something else. The page each individual row is on is not asserted here - that is
 * {@code CommunityModerationPagesIT}'s subject, and it asks for a page large enough to hold
 * whatever else has accumulated.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:smartfix-community-queue-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "smartfix.bootstrap-admin.enabled=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CommunityModerationQueuePagingIT {

    // Synthetic test-only credential, never an application default.
    private static final String PASSWORD = "TestPassword9";

    private static final Instant BASE = Instant.parse("2026-09-20T08:00:00Z");

    private static final String QUEUE = "/admin/community/reports";

    private static final int REPORTS = 12;

    @Autowired private MockMvc mvc;
    @Autowired private UserService users;
    @Autowired private CommunityQuestionRepository questions;
    @Autowired private CommunityReportRepository reports;

    private Long askerId;
    private Long reporterId;

    @BeforeAll
    void accounts() {
        askerId = createAccount("asker", Role.REQUESTER);
        reporterId = createAccount("reporter", Role.REQUESTER);
        createAccount("root.admin", Role.ADMINISTRATOR);
    }

    /**
     * An empty queue invites the first case rather than blaming a filter or a page.
     *
     * <p>It reads the table, so it has to run before anything writes to it - hence the
     * ordering annotations on this class.</p>
     */
    @Test
    @Order(1)
    void anEmptyQueueSaysItIsEmptyRatherThanBlamingSomethingElse() throws Exception {
        mvc.perform(get(QUEUE).session(login("root.admin")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No reports are waiting.")))
                .andExpect(content().string(containsString(
                        "Reports filed by readers on questions and answers appear here for a "
                                + "moderator to decide.")))
                // Nothing is waiting, so there is no count to print and no page to turn.
                .andExpect(content().string(containsString("0 reports")))
                .andExpect(content().string(not(containsString("Next"))))
                .andExpect(content().string(not(containsString("Previous"))));
    }

    /**
     * Twelve reports, ten to a page, oldest first.
     *
     * <p>Both facts the pager is built from are checked on both sides: the first page holds
     * the oldest ten and offers a way forward, the second holds the last two and offers a
     * way back, and neither offers a page that does not exist.</p>
     */
    @Test
    @Order(2)
    void theQueuePagesTheOldestReportsFirstAndCarriesTheSizeForward() throws Exception {
        for (int index = 0; index < REPORTS; index++) {
            seed(index);
        }

        String first = mvc.perform(get(QUEUE).param("size", "10").session(login("root.admin")))
                .andExpect(status().isOk())
                // The oldest ten are here; the eleventh is not on this page.
                .andExpect(content().string(containsString("Paging marker 0")))
                .andExpect(content().string(containsString("Paging marker 9")))
                .andExpect(content().string(not(containsString("Paging marker 10"))))
                .andExpect(content().string(containsString(REPORTS + " reports")))
                // Whatever size was asked for travels into the link, rather than the pager
                // silently resetting to the default on the way to the next page.
                .andExpect(content().string(containsString("size=10")))
                .andExpect(content().string(containsString("page=1")))
                .andReturn().getResponse().getContentAsString();

        // There is no page before the first, so nothing offers one.
        assertThat(first).doesNotContain("page=-1");
        assertThat(first).doesNotContain("Previous");

        String second = mvc.perform(get(QUEUE).param("page", "1").param("size", "10")
                        .session(login("root.admin")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Paging marker 10")))
                .andExpect(content().string(containsString("Paging marker 11")))
                .andExpect(content().string(not(containsString("Paging marker 0"))))
                .andExpect(content().string(containsString("page=0")))
                .andExpect(content().string(containsString("size=10")))
                .andReturn().getResponse().getContentAsString();

        // There is nothing after the last, so nothing offers one.
        assertThat(second).doesNotContain("page=2");
        assertThat(second).doesNotContain("Next");
    }

    /**
     * A request that names no size gets the configured default and not the whole table.
     *
     * <p>This is also the observable half of the ceiling rule. The maximum of 50 is what
     * {@code resolveSize} clamps to, and with a dozen rows the clamped value never differs
     * from the requested one - so a test here would assert nothing. Where the capped value
     * is visible is the board's filter form, which carries the size the page is actually
     * using, and that is asserted in {@code CommunityPagesIT}.</p>
     */
    @Test
    @Order(3)
    void noSizeAtAllMeansTheConfiguredDefaultRatherThanTheWholeTable() throws Exception {
        mvc.perform(get(QUEUE).session(login("root.admin")))
                .andExpect(status().isOk())
                // Twelve reports exist and ten are shown, so a page is bounded even when
                // the request says nothing about size.
                .andExpect(content().string(containsString("Paging marker 9")))
                .andExpect(content().string(not(containsString("Paging marker 10"))))
                .andExpect(content().string(containsString("size=10")))
                .andExpect(content().string(containsString("page=1")));
    }

    // -------------------------------------------------------------- helpers

    /** Seeds one report with a timestamp after every report before it. */
    private void seed(int index) {
        CommunityQuestion question = questions.saveAndFlush(CommunityQuestion.ask(
                askerId,
                "Paging marker " + index,
                "A question seeded so the queue has more than one page of reports.",
                CommunityCategory.OTHER,
                BASE.plusSeconds(60L * index)));
        reports.saveAndFlush(CommunityReport.ofQuestion(
                reporterId,
                question.getId(),
                CommunityReportReason.OTHER,
                null,
                BASE.plusSeconds(60L * index)));
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
        return (MockHttpSession) mvc.perform(post("/login").with(csrf())
                        .param("username", username).param("password", PASSWORD))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/"))
                .andReturn().getRequest().getSession(false);
    }
}
