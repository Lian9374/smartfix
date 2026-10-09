package com.smartfix.community.repository;

import com.smartfix.community.domain.CommunityAnswer;
import com.smartfix.community.domain.CommunityCategory;
import com.smartfix.community.domain.CommunityContentStatus;
import com.smartfix.community.domain.CommunityQuestion;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The community queries, against a schema Hibernate builds from the entities.
 *
 * <p>What this test is for: the search, the three tabs, the topic filter and the stable
 * sort are all expressed in JPQL, and JPQL is the one part of the module that compiles
 * whether or not it is correct. A query that names a property that does not exist, or
 * uses an enum literal the provider cannot resolve, fails here or nowhere.</p>
 *
 * <p>What it cannot check, and does not pretend to: the composite foreign key that makes
 * "the accepted answer belongs to this question" true. H2 gets its schema from the
 * entities, and the entity deliberately has no such association, so this database has no
 * constraint to violate. {@code CommunityMigrationPostgresIT} covers it under the
 * {@code postgres-it} profile, which is the only place the real constraint exists.</p>
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@ActiveProfiles("test")
class CommunityQuestionRepositoryTest {

    private static final Long AUTHOR_ONE = 11L;
    private static final Long AUTHOR_TWO = 12L;
    private static final Long AUTHOR_THREE = 13L;

    /** Every category, which is how the service says "no topic filter". */
    private static final EnumSet<CommunityCategory> ANY_TOPIC =
            EnumSet.allOf(CommunityCategory.class);

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    @Autowired private CommunityQuestionRepository questions;
    @Autowired private CommunityAnswerRepository answers;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void batchCountsExcludeHiddenWithdrawnAndOffPageAnswers() {
        var now = Instant.parse("2026-10-09T08:00:00Z");
        var one = questions.saveAndFlush(CommunityQuestion.ask(AUTHOR_ONE,
                "First count question", "A synthetic question body long enough to pass validation.", CommunityCategory.OTHER, now));
        var two = questions.saveAndFlush(CommunityQuestion.ask(AUTHOR_ONE,
                "Second count question", "A synthetic question body long enough to pass validation.", CommunityCategory.OTHER, now));
        var offPage = questions.saveAndFlush(CommunityQuestion.ask(AUTHOR_ONE,
                "An off page question", "A synthetic question body long enough to pass validation.", CommunityCategory.OTHER, now));
        answers.save(CommunityAnswer.post(one.getId(), AUTHOR_TWO, "This is a visible synthetic answer.", now));
        var hidden = CommunityAnswer.post(one.getId(), AUTHOR_TWO, "This hidden answer must not contribute to the count.", now);
        hidden.hide(now);
        answers.save(hidden);
        var withdrawn = CommunityAnswer.post(one.getId(), AUTHOR_TWO, "This withdrawn answer must not contribute to the count.", now);
        withdrawn.withdraw(now);
        answers.save(withdrawn);
        answers.saveAndFlush(CommunityAnswer.post(offPage.getId(), AUTHOR_TWO, "This answer is outside the requested page.", now));
        var counts = answers.countVisibleByQuestionIds(java.util.List.of(one.getId(), two.getId()));
        assertThat(counts).hasSize(1);
        assertThat(counts.getFirst().getQuestionId()).isEqualTo(one.getId());
        assertThat(counts.getFirst().getAnswerCount()).isEqualTo(1);
    }

    @Test
    void publiclyVisibleSearchLeavesOutHiddenAndWithdrawnQuestions() {
        question(AUTHOR_ONE, "Printer jams on the third floor",
                "The printer jams every time it feeds from tray two.", CommunityCategory.PERIPHERAL,
                "2026-09-20T08:00:00Z");
        CommunityQuestion hidden = question(AUTHOR_ONE, "Printer queue is stuck",
                "Nothing in the queue ever starts printing.", CommunityCategory.PERIPHERAL,
                "2026-09-20T09:00:00Z");
        CommunityQuestion withdrawn = question(AUTHOR_TWO, "Printer makes a grinding noise",
                "The printer grinds loudly at the start of every job.", CommunityCategory.PERIPHERAL,
                "2026-09-20T10:00:00Z");
        setStatus(hidden, "HIDDEN");
        setStatus(withdrawn, "WITHDRAWN");

        Page<CommunityQuestion> found =
                questions.searchVisible(ANY_TOPIC, "%printer%", firstPage());

        assertThat(found.getContent())
                .extracting(CommunityQuestion::getTitle)
                .containsExactly("Printer jams on the third floor");
        // The count has to exclude them too. A total that counted hidden rows would
        // advertise pages that the query itself can never fill.
        assertThat(found.getTotalElements()).isEqualTo(1);
    }

    @Test
    void searchMatchesTheTitleOrTheBodyIgnoringCase() {
        question(AUTHOR_ONE, "Wifi drops in the library", "The network drops every few minutes.",
                CommunityCategory.NETWORK, "2026-09-20T08:00:00Z");
        question(AUTHOR_ONE, "Projector will not power on",
                "The projector does not respond; the WIFI indicator stays dark.",
                CommunityCategory.HARDWARE, "2026-09-20T09:00:00Z");
        question(AUTHOR_ONE, "Door access card is dead",
                "The card reader no longer recognises my card.", CommunityCategory.HARDWARE,
                "2026-09-20T10:00:00Z");

        // One match in a title, one in a body, none in the third question.
        assertThat(questions.searchVisible(ANY_TOPIC, "%wifi%", firstPage()).getContent())
                .extracting(CommunityQuestion::getTitle)
                .containsExactlyInAnyOrder(
                        "Wifi drops in the library",
                        "Projector will not power on");
    }

    @Test
    void unansweredMeansNoAcceptedAnswerRatherThanNoReplies() {
        CommunityQuestion open = question(AUTHOR_ONE, "Screen flickers when docked",
                "The external screen flickers whenever the laptop is docked.",
                CommunityCategory.HARDWARE, "2026-09-20T08:00:00Z");
        question(AUTHOR_ONE, "Keyboard repeats letters",
                "Typing one letter produces two or three of them.", CommunityCategory.PERIPHERAL,
                "2026-09-20T09:00:00Z");
        CommunityQuestion settled = question(AUTHOR_TWO, "Monitor stays black",
                "The second monitor never receives a signal.", CommunityCategory.HARDWARE,
                "2026-09-20T10:00:00Z");
        acceptAnAnswerFor(settled, "It was the display cable all along.");
        // The open one has replies of its own, which must not make it "answered".
        answers.saveAndFlush(CommunityAnswer.post(open.getId(), AUTHOR_TWO,
                "Try a different cable first.", Instant.parse("2026-09-20T11:00:00Z")));

        assertThat(questions.searchVisibleUnanswered(ANY_TOPIC, "%", firstPage()).getContent())
                .extracting(CommunityQuestion::getTitle)
                .containsExactlyInAnyOrder("Screen flickers when docked", "Keyboard repeats letters");
        assertThat(questions.searchVisibleSolved(ANY_TOPIC, "%", firstPage()).getContent())
                .extracting(CommunityQuestion::getTitle)
                .containsExactly("Monitor stays black");
    }

    @Test
    void topicFilterRestrictsTheResultToTheChosenCategory() {
        question(AUTHOR_ONE, "Wifi drops in the library", "The network drops every few minutes.",
                CommunityCategory.NETWORK, "2026-09-20T08:00:00Z");
        question(AUTHOR_ONE, "Projector will not power on",
                "The projector does not respond at all.", CommunityCategory.HARDWARE,
                "2026-09-20T09:00:00Z");

        assertThat(questions.searchVisible(EnumSet.of(CommunityCategory.NETWORK), "%", firstPage())
                .getContent())
                .extracting(CommunityQuestion::getTitle)
                .containsExactly("Wifi drops in the library");
    }

    @Test
    void pagingIsStableAndTheTotalIsTheFilteredTotal() {
        for (int index = 0; index < 5; index++) {
            question(AUTHOR_ONE, "Question number " + index,
                    "Body text long enough to pass the minimum length rule.",
                    CommunityCategory.OTHER,
                    "2026-09-20T0" + index + ":00:00Z");
        }

        Page<CommunityQuestion> first = questions.searchVisible(
                ANY_TOPIC, "%", PageRequest.of(0, 2, NEWEST_FIRST));
        Page<CommunityQuestion> second = questions.searchVisible(
                ANY_TOPIC, "%", PageRequest.of(1, 2, NEWEST_FIRST));

        assertThat(first.getTotalElements()).isEqualTo(5);
        assertThat(first.getTotalPages()).isEqualTo(3);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.getContent()).extracting(CommunityQuestion::getTitle)
                .containsExactly("Question number 4", "Question number 3");
        assertThat(second.getContent()).extracting(CommunityQuestion::getTitle)
                .containsExactly("Question number 2", "Question number 1");
        assertThat(second.hasPrevious()).isTrue();
    }

    @Test
    void myQuestionsIncludesWithdrawnOnesAndOnlyThatAuthors() {
        CommunityQuestion mine = question(AUTHOR_ONE, "Wifi drops in the library",
                "The network drops every few minutes.", CommunityCategory.NETWORK,
                "2026-09-20T08:00:00Z");
        CommunityQuestion mineWithdrawn = question(AUTHOR_ONE, "Projector will not power on",
                "The projector does not respond at all.", CommunityCategory.HARDWARE,
                "2026-09-20T09:00:00Z");
        question(AUTHOR_TWO, "Door access card is dead",
                "The card reader no longer recognises my card.", CommunityCategory.HARDWARE,
                "2026-09-20T10:00:00Z");
        setStatus(mineWithdrawn, "WITHDRAWN");

        Page<CommunityQuestion> found = questions.findByAuthorId(AUTHOR_ONE, firstPage());

        assertThat(found.getContent()).extracting(CommunityQuestion::getId)
                .containsExactlyInAnyOrder(mine.getId(), mineWithdrawn.getId());
        assertThat(found.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findByIdAndAuthorIdAnswersEmptyForSomeoneElsesQuestion() {
        CommunityQuestion mine = question(AUTHOR_ONE, "Wifi drops in the library",
                "The network drops every few minutes.", CommunityCategory.NETWORK,
                "2026-09-20T08:00:00Z");

        assertThat(questions.findByIdAndAuthorId(mine.getId(), AUTHOR_ONE)).isPresent();
        // Empty, not an exception and not a forbidden-looking result: the service turns
        // this into a 404, which is what keeps the id of a stranger's question secret.
        assertThat(questions.findByIdAndAuthorId(mine.getId(), AUTHOR_TWO)).isEmpty();
    }

    @Test
    void repeatingGuardsCountOnlyThisAuthorInsideTheWindow() {
        Instant windowStart = Instant.parse("2026-09-20T08:00:00Z");
        question(AUTHOR_ONE, "Wifi drops in the library",
                "The network drops every few minutes.", CommunityCategory.NETWORK,
                "2026-09-20T09:00:00Z");
        question(AUTHOR_TWO, "Wifi drops in the library",
                "The network drops every few minutes.", CommunityCategory.NETWORK,
                "2026-09-20T09:00:00Z");
        question(AUTHOR_ONE, "An older question entirely",
                "Asked long before the window opens.", CommunityCategory.OTHER,
                "2026-09-20T07:00:00Z");

        assertThat(questions.countByAuthorIdAndCreatedAtGreaterThanEqual(AUTHOR_ONE, windowStart))
                .isEqualTo(1);
        // Case-insensitively the same text as the row above, by a different author, so it
        // must not count towards this author's duplicate guard.
        assertThat(questions.countByAuthorIdAndTitleIgnoreCaseAndBodyIgnoreCaseAndCreatedAtGreaterThanEqual(
                AUTHOR_THREE, "WIFI DROPS IN THE LIBRARY",
                "The network drops every few minutes.", windowStart)).isZero();
        assertThat(questions.countByAuthorIdAndTitleIgnoreCaseAndBodyIgnoreCaseAndCreatedAtGreaterThanEqual(
                AUTHOR_ONE, "WIFI DROPS IN THE LIBRARY",
                "The network drops every few minutes.", windowStart)).isEqualTo(1);
    }

    private Pageable firstPage() {
        return PageRequest.of(0, 10, NEWEST_FIRST);
    }

    private CommunityQuestion question(
            Long authorId,
            String title,
            String body,
            CommunityCategory category,
            String createdAt) {
        return questions.saveAndFlush(CommunityQuestion.ask(
                authorId, title, body, category, Instant.parse(createdAt)));
    }

    /** Moves a question out of public view without going through the author's own flow. */
    private void setStatus(CommunityQuestion question, String status) {
        jdbc.update("UPDATE community_questions SET status = ? WHERE id = ?", status, question.getId());
    }

    /**
     * Accepts an answer for a question.
     *
     * <p>Written through JDBC rather than through a domain method because accepting is a
     * later phase and the entity exposes no way to do it. The answer row is real, so the
     * data this produces would also satisfy the composite foreign key on PostgreSQL -
     * which is why the accepted id is read back from an inserted answer rather than
     * invented.</p>
     */
    private void acceptAnAnswerFor(CommunityQuestion question, String body) {
        CommunityAnswer answer = answers.saveAndFlush(CommunityAnswer.post(
                question.getId(), AUTHOR_TWO, body, Instant.parse("2026-09-20T11:30:00Z")));
        jdbc.update("UPDATE community_questions SET accepted_answer_id = ? WHERE id = ?",
                answer.getId(), question.getId());
    }
}
