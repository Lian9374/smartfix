package com.smartfix.community.web;

import com.smartfix.community.domain.CommunityAnswer;
import com.smartfix.community.domain.CommunityCategory;
import com.smartfix.community.domain.CommunityQuestion;
import com.smartfix.community.repository.CommunityAnswerRepository;
import com.smartfix.community.repository.CommunityQuestionRepository;
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
 * The four community pages, rendered by real controllers, real services and real
 * Thymeleaf, over H2, behind real Spring Security.
 *
 * <h2>Security</h2>
 *
 * <p>Every request here goes through the production {@code SecurityFilterChain}, with no
 * test-scoped chain standing in for it. That was not always so: while
 * {@code /community/**} had no rule and fell through to {@code anyRequest().denyAll()},
 * a test-only chain had to be imported for the pages to be reachable at all. The rule
 * {@code .requestMatchers("/community/**").authenticated()} is now in SecurityConfig, so
 * that stand-in is gone and these tests exercise the real authorisation - including that
 * a signed-out visitor is redirected to {@code /login} rather than reaching the board.</p>
 *
 * <h2>What only this layer can catch</h2>
 *
 * <p>A controller test asserts a view name; a template error is invisible to it. A
 * fragment called with the wrong arity, a property read through the wrong accessor, a
 * nested conditional, a link built with a parameter that does not exist - each is a
 * runtime failure. So the assertions below are about what the pages actually say: the
 * rendered title, the escaped body, the pager's carried-forward filters, the two
 * different empty states, and the routing precedence between
 * {@code /community/questions/new} and {@code /community/questions/{questionId}}.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:smartfix-community-pages-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "smartfix.bootstrap-admin.enabled=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommunityPagesIT {

    // Synthetic test-only credential, never an application default.
    private static final String PASSWORD = "TestPassword9";

    private static final Instant BASE = Instant.parse("2026-09-20T08:00:00Z");

    @Autowired private MockMvc mvc;
    @Autowired private UserService users;
    @Autowired private CommunityQuestionRepository questions;
    @Autowired private CommunityAnswerRepository answers;
    @Autowired private JdbcTemplate jdbc;

    private Long aliceId;
    private Long bobId;
    private Long pagerId;

    private int seeded;

    @BeforeAll
    void accounts() {
        aliceId = createAccount("alice", Role.REQUESTER);
        bobId = createAccount("bob", Role.REQUESTER);
        // Created for their roles, and signed in by name; their ids are not asserted on.
        createAccount("root.admin", Role.ADMINISTRATOR);
        createAccount("tech", Role.TECHNICIAN);
        createAccount("quiet", Role.REQUESTER);
        pagerId = createAccount("pager", Role.REQUESTER);
    }

    // ---------------------------------------------------------------- the board

    @Test
    void theBoardRendersTheVisibleQuestionsWithTheirRealFields() throws Exception {
        CommunityQuestion question = seed(aliceId, "Printer jams on the third floor",
                "The printer jams every time it feeds from tray two.",
                CommunityCategory.PERIPHERAL);

        mvc.perform(get("/community").session(login("bob")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Questions and answers from across the campus.")))
                .andExpect(content().string(containsString("Printer jams on the third floor")))
                .andExpect(content().string(containsString(
                        "The printer jams every time it feeds from tray two.")))
                .andExpect(content().string(containsString("href=\"/community/questions/"
                        + question.getId() + "\"")))
                .andExpect(content().string(containsString("PERIPHERAL")))
                // No answer can be accepted yet, so every row reads Open.
                .andExpect(content().string(containsString(">Open<")))
                .andExpect(content().string(containsString("alice (test)")))
                // The two actions a reader can take from here.
                .andExpect(content().string(containsString("href=\"/community/questions/new\"")))
                .andExpect(content().string(containsString("href=\"/community/mine\"")));
    }

    @Test
    void searchAndFiltersAreAppliedAndSaidSoWhenTheyMatchNothing() throws Exception {
        seed(aliceId, "Projector will not power on",
                "The projector in seminar room three shows no picture at all.",
                CommunityCategory.HARDWARE);

        mvc.perform(get("/community").param("q", "zzzznothingmatchesthis")
                        .session(login("bob")))
                .andExpect(status().isOk())
                // The filter-matched-nothing state, not the board-is-empty one.
                .andExpect(content().string(containsString("No questions match this page or filter.")))
                .andExpect(content().string(not(containsString("No questions yet"))))
                // What was typed is handed back to the search box.
                .andExpect(content().string(containsString("value=\"zzzznothingmatchesthis\"")));
    }

    @Test
    void searchLooksInTheBodyAsWellAsTheTitleAndIgnoresCase() throws Exception {
        seed(aliceId, "Door access card is dead",
                "The reader near the loading bay says BAYSIDE on its screen.",
                CommunityCategory.HARDWARE);

        mvc.perform(get("/community").param("q", "bayside").session(login("bob")))
                .andExpect(status().isOk())
                // The word is in the body, not the title, and was typed in a different case.
                .andExpect(content().string(containsString("Door access card is dead")));
    }

    @Test
    void theTopicFilterNarrowsTheListToThatTopic() throws Exception {
        seed(aliceId, "Topic filter network sample",
                "A question about the wireless network in the west wing.",
                CommunityCategory.NETWORK);
        seed(aliceId, "Topic filter hardware sample",
                "A question about a broken monitor in the east wing.",
                CommunityCategory.HARDWARE);

        mvc.perform(get("/community").param("category", "NETWORK").session(login("bob")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Topic filter network sample")))
                .andExpect(content().string(not(containsString("Topic filter hardware sample"))));
    }

    /**
     * The two tabs are the tab most likely to be broken by a query that treats "no
     * answers" and "no accepted answer" as the same thing, so both sides are asserted on
     * a question that really does have an accepted answer.
     *
     * <p>The acceptance is made through the route rather than by writing the column, so
     * the tab is exercised against a state the application can actually reach.</p>
     */
    @Test
    void theAnsweredTabsSeparateOpenQuestionsFromSolvedOnes() throws Exception {
        seed(aliceId, "Tab filter open sample",
                "A question whose answer nobody has accepted.", CommunityCategory.OTHER);
        CommunityQuestion solved = seed(aliceId, "Tab filter solved sample",
                "A question whose answer its author accepted.", CommunityCategory.OTHER);
        CommunityAnswer answer = answers.saveAndFlush(CommunityAnswer.post(solved.getId(), bobId,
                "An answer the question's author will accept.", BASE.plusSeconds(3600)));

        mvc.perform(post("/community/questions/" + solved.getId() + "/answers/" + answer.getId()
                        + "/accept").with(csrf()).session(login("alice")))
                .andExpect(status().isFound());

        mvc.perform(get("/community").param("filter", "UNANSWERED").session(login("bob")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tab filter open sample")))
                .andExpect(content().string(not(containsString("Tab filter solved sample"))));

        mvc.perform(get("/community").param("filter", "SOLVED").session(login("bob")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tab filter solved sample")))
                .andExpect(content().string(not(containsString("Tab filter open sample"))));

        assertThat(questions.findById(solved.getId()).orElseThrow().isSolved()).isTrue();
    }

    @Test
    void aSearchTermOverTheLengthLimitIsRefused() throws Exception {
        mvc.perform(get("/community").param("q", "x".repeat(101)).session(login("bob")))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------- routing precedence

    @Test
    void theQuestionFormIsNotSwallowedByTheVariableDetailRoute() throws Exception {
        mvc.perform(get("/community/questions/new").session(login("alice")))
                .andExpect(status().isOk())
                // The form, not the detail page for a question with the id "new".
                .andExpect(content().string(containsString("Share a question with your campus.")))
                .andExpect(content().string(containsString("action=\"/community/questions\"")))
                .andExpect(content().string(containsString("name=\"title\"")))
                .andExpect(content().string(containsString("name=\"body\"")))
                .andExpect(content().string(containsString("name=\"category\"")))
                .andExpect(content().string(not(containsString("Question summary"))));
    }

    @Test
    void aRealQuestionIdStillReachesTheDetailPage() throws Exception {
        CommunityQuestion question = seed(aliceId, "Routing precedence sample",
                "A question that exists so its id can be fetched.", CommunityCategory.OTHER);

        mvc.perform(get("/community/questions/" + question.getId()).session(login("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Question summary")))
                .andExpect(content().string(containsString("Routing precedence sample")));
    }

    // -------------------------------------------------------------- writing

    @Test
    void postingAQuestionStoresItAndRedirectsToIt() throws Exception {
        MvcResult result = mvc.perform(post("/community/questions").with(csrf())
                        .session(login("alice"))
                        .param("title", "Camera in the atrium is offline")
                        .param("body", "The camera above the main entrance has shown nothing since Tuesday.")
                        .param("category", "HARDWARE"))
                .andExpect(status().isFound())
                .andReturn();

        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).startsWith("/community/questions/");

        // POST-Redirect-GET: the redirect target renders the question that was stored.
        mvc.perform(get(location).session(login("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Camera in the atrium is offline")))
                .andExpect(content().string(containsString(
                        "The camera above the main entrance has shown nothing since Tuesday.")));
    }

    /**
     * The three fields a browser must not be able to set. They are submitted here on
     * purpose: if the command ever grew one of them, this test would store a question
     * attributed to somebody else - or one that is already hidden, or already solved -
     * and the assertions below would fail.
     */
    @Test
    void forgedAuthorStatusAndAcceptedAnswerFieldsAreDiscarded() throws Exception {
        String title = "Forged field sample question";
        MvcResult result = mvc.perform(post("/community/questions").with(csrf())
                        .session(login("alice"))
                        .param("title", title)
                        .param("body", "A question posted with author, status and accepted answer forged.")
                        .param("category", "OTHER")
                        .param("authorId", String.valueOf(bobId))
                        .param("status", "HIDDEN")
                        .param("acceptedAnswerId", "999")
                        .param("id", "4242")
                        .param("version", "77"))
                .andExpect(status().isFound())
                .andReturn();

        CommunityQuestion stored = questions
                .findById(idFrom(result.getResponse().getRedirectedUrl()))
                .orElseThrow();

        assertThat(stored.getAuthorId()).isEqualTo(aliceId);
        assertThat(stored.getStatus().name()).isEqualTo("VISIBLE");
        assertThat(stored.getAcceptedAnswerId()).isNull();
        assertThat(stored.isSolved()).isFalse();
    }

    @Test
    void anInvalidTitleIsRefusedWithAMessageAndTheTypedTextIsKept() throws Exception {
        String body = "This body is long enough to pass its own minimum length rule.";

        mvc.perform(post("/community/questions").with(csrf())
                        .session(login("alice"))
                        .param("title", "ab")
                        .param("body", body)
                        .param("category", "OTHER"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("must be between 3 and 150 characters")))
                // Nothing typed is lost when the form comes back.
                .andExpect(content().string(containsString("value=\"ab\"")))
                .andExpect(content().string(containsString(body)));
    }

    @Test
    void aMissingTopicIsRefused() throws Exception {
        mvc.perform(post("/community/questions").with(csrf())
                        .session(login("alice"))
                        .param("title", "A title long enough to pass")
                        .param("body", "A body long enough to pass its minimum length rule.")
                        .param("category", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Topic is required.")));
    }

    @Test
    void aPostWithoutACsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/community/questions")
                        .session(login("alice"))
                        .param("title", "A title long enough to pass")
                        .param("body", "A body long enough to pass its minimum length rule.")
                        .param("category", "OTHER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aWithdrawWithoutACsrfTokenIsRejected() throws Exception {
        CommunityQuestion question = seed(aliceId, "Csrf withdrawal sample",
                "A question that must survive a post with no token.", CommunityCategory.OTHER);

        mvc.perform(post("/community/questions/" + question.getId() + "/withdraw")
                        .session(login("alice")))
                .andExpect(status().isForbidden());

        assertThat(questions.findById(question.getId()).orElseThrow().getStatus().name())
                .isEqualTo("VISIBLE");
    }

    // ------------------------------------------------------------- the detail

    @Test
    void theDetailPageRendersTheBodyAndOffersAnAnswerBox() throws Exception {
        CommunityQuestion question = seed(aliceId, "Detail page sample question",
                "First line of the description.\nSecond line of the description.",
                CommunityCategory.SOFTWARE);

        mvc.perform(get("/community/questions/" + question.getId()).session(login("bob")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Detail page sample question")))
                // The line break survives into the markup; the stylesheet is what makes it
                // visible, so it has to be in the text and not merely in the CSS.
                .andExpect(content().string(containsString(
                        "First line of the description.\nSecond line of the description.")))
                .andExpect(content().string(containsString("No answers yet")))
                // Any signed-in account may answer, so the box is there for a reader who
                // did not ask the question, and it posts to the answer route.
                .andExpect(content().string(containsString(
                        "action=\"/community/questions/" + question.getId() + "/answers\"")))
                .andExpect(content().string(containsString("<textarea")))
                .andExpect(content().string(containsString("alice (test)")));
    }

    /**
     * A withdrawn question shows no answer box.
     *
     * <p>Its author can still read the thread, and the page has to say why nobody can
     * answer it rather than leaving the author to wonder why every other thread has a
     * box and theirs does not.</p>
     */
    @Test
    void aQuestionThatIsNotPublicOffersNoAnswerBox() throws Exception {
        CommunityQuestion question = seed(aliceId, "Withdrawn question sample",
                "A question its author took back before anyone answered it.",
                CommunityCategory.OTHER);
        jdbc.update("UPDATE community_questions SET status = ? WHERE id = ?",
                "WITHDRAWN", question.getId());

        mvc.perform(get("/community/questions/" + question.getId()).session(login("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("This question cannot be answered")))
                .andExpect(content().string(not(containsString(
                        "action=\"/community/questions/" + question.getId() + "/answers\""))))
                .andExpect(content().string(not(containsString("<textarea"))));
    }

    @Test
    void aWithdrawnAnswerKeepsItsPlaceButNotItsText() throws Exception {
        CommunityQuestion question = seed(aliceId, "Withheld answer sample question",
                "A question with one answer that has been withdrawn.", CommunityCategory.OTHER);
        CommunityAnswer answer = answers.saveAndFlush(CommunityAnswer.post(question.getId(), bobId,
                "A reply that will be withdrawn before anyone reads it.", BASE.plusSeconds(60)));
        jdbc.update("UPDATE community_answers SET status = ? WHERE id = ?", "WITHDRAWN", answer.getId());

        mvc.perform(get("/community/questions/" + question.getId()).session(login("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("This answer was withdrawn.")))
                .andExpect(content().string(not(containsString(
                        "A reply that will be withdrawn before anyone reads it."))));
    }

    /**
     * Markup in a title or a body must arrive as text. The page contains real
     * {@code <script>} elements of its own, so the assertion is on the injected string
     * rather than on the tag, and on the escaped form being present rather than only the
     * raw form being absent - otherwise a page that dropped the field entirely would pass.
     */
    @Test
    void markupInAQuestionIsEscapedAndNeverRendered() throws Exception {
        String injection = "<script>alert('community')</script>";
        String imageInjection = "<img src=x onerror=\"alert('community')\">";
        CommunityQuestion question = seed(aliceId, injection,
                "A body carrying " + imageInjection + " inside it.", CommunityCategory.OTHER);

        mvc.perform(get("/community").param("q", "community").session(login("bob")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;alert(&#39;community&#39;)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString(injection))));

        mvc.perform(get("/community/questions/" + question.getId()).session(login("bob")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(containsString("&lt;img src=x onerror=")))
                .andExpect(content().string(not(containsString(imageInjection))));
    }

    @Test
    void aQuestionThatDoesNotExistIsANotFound() throws Exception {
        mvc.perform(get("/community/questions/987654").session(login("alice")))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------- editing

    @Test
    void editingSomeoneElsesQuestionIsANotFoundOnBothTheFormAndThePost() throws Exception {
        CommunityQuestion question = seed(aliceId, "Ownership sample question",
                "A question only its author may change.", CommunityCategory.OTHER);

        mvc.perform(get("/community/questions/" + question.getId() + "/edit")
                        .session(login("bob")))
                .andExpect(status().isNotFound());

        mvc.perform(post("/community/questions/" + question.getId()).with(csrf())
                        .session(login("bob"))
                        .param("title", "Hijacked by somebody else")
                        .param("body", "This body is long enough to pass the minimum length rule.")
                        .param("category", "OTHER"))
                .andExpect(status().isNotFound());

        CommunityQuestion untouched = questions.findById(question.getId()).orElseThrow();
        assertThat(untouched.getTitle()).isEqualTo("Ownership sample question");
    }

    @Test
    void anAuthorCanOpenTheEditFormPrefilledAndSaveAChange() throws Exception {
        CommunityQuestion question = seed(aliceId, "Editable sample question",
                "The original description of the problem.",
                CommunityCategory.NETWORK);

        mvc.perform(get("/community/questions/" + question.getId() + "/edit")
                        .session(login("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"Editable sample question\"")))
                .andExpect(content().string(containsString(
                        "The original description of the problem.")))
                // The edit form targets the question, not the create route.
                .andExpect(content().string(containsString(
                        "action=\"/community/questions/" + question.getId() + "\"")));

        mvc.perform(post("/community/questions/" + question.getId()).with(csrf())
                        .session(login("alice"))
                        .param("title", "Edited sample question title")
                        .param("body", "The corrected description of the problem.")
                        .param("category", "HARDWARE"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/community/questions/" + question.getId()));

        CommunityQuestion edited = questions.findById(question.getId()).orElseThrow();
        assertThat(edited.getTitle()).isEqualTo("Edited sample question title");
        assertThat(edited.getCategory()).isEqualTo(CommunityCategory.HARDWARE);
        // Neither field the form does not carry was reachable from the edit.
        assertThat(edited.getAuthorId()).isEqualTo(aliceId);
        assertThat(edited.getAcceptedAnswerId()).isNull();
    }

    @Test
    void aRejectedEditKeepsTheAuthorOnTheFormWithTheirText() throws Exception {
        CommunityQuestion question = seed(aliceId, "Rejected edit sample question",
                "A question whose edit will be refused.", CommunityCategory.OTHER);

        mvc.perform(post("/community/questions/" + question.getId()).with(csrf())
                        .session(login("alice"))
                        .param("title", "ab")
                        .param("body", "This body is long enough to pass the minimum length rule.")
                        .param("category", "OTHER"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("must be between 3 and 150 characters")))
                .andExpect(content().string(containsString("value=\"ab\"")));
    }

    @Test
    void anEditThatIsRefusedDoesNotBecomeAFormOnSomeoneElsesQuestion() throws Exception {
        CommunityQuestion question = seed(aliceId, "Refused edit ownership sample",
                "A question that will be edited with invalid text by a stranger.",
                CommunityCategory.OTHER);

        mvc.perform(post("/community/questions/" + question.getId()).with(csrf())
                        .session(login("bob"))
                        .param("title", "ab")
                        .param("body", "This body is long enough to pass the minimum length rule.")
                        .param("category", "OTHER"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------- withdrawing

    @Test
    void withdrawingTakesTheQuestionOffTheBoardButKeepsItOnMyQuestions() throws Exception {
        CommunityQuestion question = seed(aliceId, "Withdrawal lifecycle sample",
                "A question that will be withdrawn by its author.", CommunityCategory.OTHER);

        mvc.perform(post("/community/questions/" + question.getId() + "/withdraw").with(csrf())
                        .session(login("alice")))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/community/mine"));

        mvc.perform(get("/community").param("q", "Withdrawal lifecycle sample")
                        .session(login("bob")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No questions match this page or filter.")))
                // The search box echoes the term back, so asserting on the title would
                // fail on the echo rather than on a leaked row. The row's link is what
                // proves the question is not on the board.
                .andExpect(content().string(not(containsString(
                        "href=\"/community/questions/" + question.getId() + "\""))));

        // The author still sees it, labelled, so the action has a visible result.
        mvc.perform(get("/community/mine").session(login("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Withdrawal lifecycle sample")))
                .andExpect(content().string(containsString("Withdrawn")));
    }

    @Test
    void withdrawingSomeoneElsesQuestionIsANotFound() throws Exception {
        CommunityQuestion question = seed(aliceId, "Withdrawal ownership sample",
                "A question a stranger will try to withdraw.", CommunityCategory.OTHER);

        mvc.perform(post("/community/questions/" + question.getId() + "/withdraw").with(csrf())
                        .session(login("bob")))
                .andExpect(status().isNotFound());

        assertThat(questions.findById(question.getId()).orElseThrow().getStatus().name())
                .isEqualTo("VISIBLE");
    }

    @Test
    void aWithdrawnQuestionIsNotFoundForEveryoneButItsAuthorWhoIsToldWhy() throws Exception {
        CommunityQuestion question = seed(aliceId, "Withdrawn detail sample",
                "A question that will be withdrawn before anyone reads it.",
                CommunityCategory.OTHER);
        jdbc.update("UPDATE community_questions SET status = ? WHERE id = ?",
                "WITHDRAWN", question.getId());

        mvc.perform(get("/community/questions/" + question.getId()).session(login("bob")))
                .andExpect(status().isNotFound());

        mvc.perform(get("/community/questions/" + question.getId()).session(login("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("You withdrew this question")))
                // No edit or withdraw control on content that is no longer public.
                .andExpect(content().string(not(containsString(
                        "href=\"/community/questions/" + question.getId() + "/edit\""))));
    }

    @Test
    void aHiddenQuestionIsNotFoundForItsAuthorTooAndSaysSo() throws Exception {
        CommunityQuestion question = seed(aliceId, "Hidden moderation sample",
                "A question a moderator will hide.", CommunityCategory.OTHER);
        jdbc.update("UPDATE community_questions SET status = ? WHERE id = ?",
                "HIDDEN", question.getId());

        mvc.perform(get("/community/questions/" + question.getId()).session(login("bob")))
                .andExpect(status().isNotFound());

        mvc.perform(get("/community/questions/" + question.getId()).session(login("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("A moderator hid it")));
    }

    // ------------------------------------------------------- my questions

    @Test
    void myQuestionsShowsOnlyTheSignedInAccountsQuestions() throws Exception {
        seed(aliceId, "Mine by alice sample",
                "A question asked by alice and only alice.", CommunityCategory.OTHER);
        seed(bobId, "Mine by bob sample",
                "A question asked by bob and only bob.", CommunityCategory.OTHER);

        mvc.perform(get("/community/mine").session(login("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your questions and their status.")))
                .andExpect(content().string(containsString("Mine by alice sample")))
                .andExpect(content().string(not(containsString("Mine by bob sample"))));
    }

    @Test
    void myQuestionsExplainsItselfWhenTheAccountHasAskedNothing() throws Exception {
        mvc.perform(get("/community/mine").session(login("quiet")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("You have not asked anything yet")))
                .andExpect(content().string(containsString("href=\"/community/questions/new\"")));
    }

    // ------------------------------------------------------------ the pager

    @Test
    void thePagerCarriesEveryFilterForwardAndPagesTheResult() throws Exception {
        for (int index = 0; index < 12; index++) {
            seed(pagerId, "Paging sample " + index,
                    "A question seeded so that zzpaging has more than one page of results.",
                    CommunityCategory.OTHER);
        }

        MvcResult firstPage = mvc.perform(get("/community")
                        .param("q", "zzpaging")
                        .param("category", "OTHER")
                        .param("size", "10")
                        .session(login("bob")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("12 discussions")))
                .andExpect(content().string(containsString("Paging sample 11")))
                .andExpect(content().string(not(containsString("Paging sample 0"))))
                .andReturn();

        String body = firstPage.getResponse().getContentAsString();
        // Every value the reader chose survives the page turn, size included.
        assertThat(body).contains("href=\"/community?page=1");
        assertThat(body).contains("size=10");
        assertThat(body).contains("category=OTHER");
        assertThat(body).contains("filter=LATEST");
        assertThat(body).contains("q=zzpaging");

        mvc.perform(get("/community")
                        .param("q", "zzpaging")
                        .param("category", "OTHER")
                        .param("size", "10")
                        .param("page", "1")
                        .session(login("bob")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Paging sample 1")))
                .andExpect(content().string(containsString("Paging sample 0")))
                .andExpect(content().string(containsString("href=\"/community?page=0")))
                .andExpect(content().string(not(containsString("Paging sample 11"))));
    }

    @Test
    void aPagePastTheEndIsAnEmptyStateRatherThanAnError() throws Exception {
        mvc.perform(get("/community").param("q", "zzpaging").param("page", "99")
                        .session(login("bob")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No questions match this page or filter.")))
                .andExpect(content().string(containsString("href=\"/community?page=98")));
    }

    @Test
    void aNegativePageReadsAsTheFirstPageAndAnOversizedSizeIsCapped() throws Exception {
        mvc.perform(get("/community").param("page", "-5").param("size", "9999")
                        .session(login("bob")))
                .andExpect(status().isOk())
                // 50 is the ceiling. The filter form carries the size the page is
                // actually using, and with too few questions to need a next-page link
                // that hidden field is the only place the capped value appears.
                .andExpect(content().string(containsString("name=\"size\" value=\"50\"")))
                // The size that was asked for reached neither the page nor a link.
                .andExpect(content().string(not(containsString("9999"))));
    }

    // ------------------------------------------------- signing in and out

    @Test
    void anUnauthenticatedVisitorIsSentToTheLoginPage() throws Exception {
        mvc.perform(get("/community"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("/login")));

        mvc.perform(get("/community/mine"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("/login")));
    }

    @Test
    void everyRoleReachesTheBoardThroughTheSameRoutes() throws Exception {
        // The controller decides nothing about roles; the same four pages are for every
        // signed-in account. What differs between roles is handled where it belongs - in
        // production's route matrix, which is the piece this module is still waiting for.
        for (String username : new String[] {"alice", "root.admin", "tech"}) {
            mvc.perform(get("/community").session(login(username)))
                    .andExpect(status().isOk());
            mvc.perform(get("/community/mine").session(login(username)))
                    .andExpect(status().isOk());
            mvc.perform(get("/community/questions/new").session(login(username)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void signingOutEndsTheSessionRatherThanOnlyRedirecting() throws Exception {
        MockHttpSession session = login("alice");
        mvc.perform(get("/community").session(session)).andExpect(status().isOk());

        mvc.perform(post("/logout").with(csrf()).session(session))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?logout"));

        mvc.perform(get("/community").session(session))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("/login")));
    }

    // ------------------------------------------------------------- structure

    @Test
    void eachPageHasExactlyOneTopLevelHeading() throws Exception {
        CommunityQuestion question = seed(aliceId, "Heading structure sample",
                "A question used to check the heading structure of the pages.",
                CommunityCategory.OTHER);

        for (String path : new String[] {
                "/community",
                "/community/mine",
                "/community/questions/new",
                "/community/questions/" + question.getId(),
                "/community/questions/" + question.getId() + "/edit"}) {
            String body = mvc.perform(get(path).session(login("alice")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(countOf(body, "<h1"))
                    .as("exactly one h1 on %s", path)
                    .isEqualTo(1);
        }
    }

    @Test
    void theFormPageStillOffersTheTopicsItCanBind() throws Exception {
        mvc.perform(get("/community/questions/new").session(login("alice")))
                .andExpect(status().isOk())
                // Every topic is offered, and each option's value is the enum the
                // controller binds rather than a display label.
                .andExpect(content().string(containsString("value=\"HARDWARE\"")))
                .andExpect(content().string(containsString("value=\"NETWORK\"")))
                .andExpect(content().string(containsString("value=\"PERIPHERAL\"")))
                .andExpect(content().string(containsString("value=\"SOFTWARE\"")))
                .andExpect(content().string(containsString("value=\"OTHER\"")))
                // The limits the server enforces are on the controls too.
                .andExpect(content().string(containsString("maxlength=\"150\"")))
                .andExpect(content().string(containsString("maxlength=\"4000\"")));
    }

    // ------------------------------------------------------------- answers

    @Test
    void anySignedInAccountCanAnswerAndTheAnswerAppearsOnTheThread() throws Exception {
        CommunityQuestion question = seed(aliceId, "Answer posting sample",
                "A question that will be answered by somebody other than its author.",
                CommunityCategory.OTHER);

        mvc.perform(post("/community/questions/" + question.getId() + "/answers").with(csrf())
                        .session(login("tech"))
                        .param("body", "Try the power cable first, then the wall socket."))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/community/questions/" + question.getId()));

        mvc.perform(get("/community/questions/" + question.getId()).session(login("bob")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Try the power cable first, then the wall socket.")))
                .andExpect(content().string(containsString("Answers (1)")));
    }

    /**
     * Markup in an answer arrives as text, in the thread and in the answer's own edit
     * form. Same reasoning as the question's version: the escaped form must be present,
     * not merely the raw form absent, or a page that dropped the field would pass.
     */
    @Test
    void markupInAnAnswerIsEscapedAndNeverRendered() throws Exception {
        String injection = "<script>alert('answer')</script>";
        CommunityQuestion question = seed(aliceId, "Answer markup sample",
                "A question whose answer will carry markup in it.", CommunityCategory.OTHER);
        CommunityAnswer answer = answers.saveAndFlush(CommunityAnswer.post(question.getId(), bobId,
                "A reply carrying " + injection + " inside it.", BASE.plusSeconds(120)));

        mvc.perform(get("/community/questions/" + question.getId()).session(login("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;alert(&#39;answer&#39;)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString(injection))));

        mvc.perform(get("/community/answers/" + answer.getId() + "/edit").session(login("bob")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString(injection))));

        // The author's own answer list shows the text too, escaped the same way.
        mvc.perform(get("/community/mine").param("tab", "ANSWERS").session(login("bob")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(injection))));
    }

    @Test
    void theQuestionAuthorAcceptsAnAnswerAndItIsPinnedFirstExactlyOnce() throws Exception {
        CommunityQuestion question = seed(aliceId, "Acceptance sample question",
                "A question whose author will accept the second of two answers.",
                CommunityCategory.OTHER);
        String firstText = "The first answer, which will not be the accepted one.";
        String secondText = "The second answer, which will be the accepted one.";
        answers.saveAndFlush(CommunityAnswer.post(question.getId(), bobId, firstText, BASE.plusSeconds(60)));
        CommunityAnswer second = answers.saveAndFlush(
                CommunityAnswer.post(question.getId(), bobId, secondText, BASE.plusSeconds(120)));

        mvc.perform(post("/community/questions/" + question.getId() + "/answers/"
                        + second.getId() + "/accept").with(csrf()).session(login("alice")))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/community/questions/" + question.getId()));

        assertThat(questions.findById(question.getId()).orElseThrow().getAcceptedAnswerId())
                .isEqualTo(second.getId());

        String body = mvc.perform(get("/community/questions/" + question.getId())
                        .session(login("bob")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">Solved<")))
                .andExpect(content().string(containsString("Accepted")))
                .andReturn().getResponse().getContentAsString();

        // Pinned, and pinned once: the accepted answer comes before the older one and
        // appears exactly as many times as it - that is, once - so it was moved rather
        // than copied.
        assertThat(body.indexOf(secondText)).isLessThan(body.indexOf(firstText));
        assertThat(countOf(body, secondText)).isEqualTo(1);
        assertThat(countOf(body, firstText)).isEqualTo(1);
        // A solved question offers no further accept buttons, only Remove acceptance.
        assertThat(body).doesNotContain("Accept this answer");
        assertThat(body).contains("Remove acceptance");
    }

    @Test
    void acceptingSomeoneElsesQuestionOrAForeignAnswerIsANotFound() throws Exception {
        CommunityQuestion question = seed(aliceId, "Acceptance ownership sample",
                "A question only its own author may accept an answer to.",
                CommunityCategory.OTHER);
        CommunityAnswer answer = answers.saveAndFlush(CommunityAnswer.post(question.getId(), bobId,
                "An answer to the question above.", BASE.plusSeconds(60)));
        CommunityQuestion other = seed(bobId, "Acceptance foreign question",
                "A different question, whose answer id will be pasted into the URL.",
                CommunityCategory.OTHER);
        CommunityAnswer otherAnswer = answers.saveAndFlush(CommunityAnswer.post(other.getId(),
                createAccount("third", Role.REQUESTER),
                "An answer belonging to the other question.", BASE.plusSeconds(60)));

        // Not the question's author.
        mvc.perform(post("/community/questions/" + question.getId() + "/answers/"
                        + answer.getId() + "/accept").with(csrf()).session(login("bob")))
                .andExpect(status().isNotFound());

        // The author, but the answer belongs to another question.
        mvc.perform(post("/community/questions/" + question.getId() + "/answers/"
                        + otherAnswer.getId() + "/accept").with(csrf()).session(login("alice")))
                .andExpect(status().isNotFound());

        assertThat(questions.findById(question.getId()).orElseThrow().getAcceptedAnswerId()).isNull();
        assertThat(questions.findById(other.getId()).orElseThrow().getAcceptedAnswerId()).isNull();
    }

    /**
     * Self-acceptance is refused. D-05 in the plan is still open, with "forbid" as the
     * recommendation, so this test pins the provisional rule in place: if the decision
     * closes the other way, this is the test that has to change.
     */
    /**
     * The refusal message travels as a flash attribute and is read on the thread the
     * redirect lands on, so the session is kept across the two requests: signing in again
     * between them would open a new session, and a flash map lives in the one that posted.
     */
    @Test
    void theQuestionAuthorCannotAcceptTheirOwnAnswer() throws Exception {
        CommunityQuestion question = seed(aliceId, "Self acceptance sample",
                "A question its author will also answer.", CommunityCategory.OTHER);
        CommunityAnswer own = answers.saveAndFlush(CommunityAnswer.post(question.getId(), aliceId,
                "The asker's own answer to their own question.", BASE.plusSeconds(60)));
        MockHttpSession session = login("alice");

        mvc.perform(post("/community/questions/" + question.getId() + "/answers/"
                        + own.getId() + "/accept").with(csrf()).session(session))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/community/questions/" + question.getId()));

        assertThat(questions.findById(question.getId()).orElseThrow().getAcceptedAnswerId()).isNull();

        // The refusal is shown on the thread, with the service's own wording.
        mvc.perform(get("/community/questions/" + question.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("You cannot accept your own answer.")));
    }

    @Test
    void acceptingTwiceIsRefusedWithAMessageRatherThanAnErrorPage() throws Exception {
        CommunityQuestion question = seed(aliceId, "Second acceptance sample",
                "A question that will be given two accepted answers in turn.",
                CommunityCategory.OTHER);
        CommunityAnswer first = answers.saveAndFlush(CommunityAnswer.post(question.getId(), bobId,
                "The answer that wins the acceptance.", BASE.plusSeconds(60)));
        CommunityAnswer second = answers.saveAndFlush(CommunityAnswer.post(question.getId(), bobId,
                "The answer that arrives too late.", BASE.plusSeconds(120)));
        MockHttpSession session = login("alice");

        mvc.perform(post("/community/questions/" + question.getId() + "/answers/"
                        + first.getId() + "/accept").with(csrf()).session(session))
                .andExpect(status().isFound());

        mvc.perform(post("/community/questions/" + question.getId() + "/answers/"
                        + second.getId() + "/accept").with(csrf()).session(session))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/community/questions/" + question.getId()));

        // The first acceptance stands, and the reader is told why the second did not.
        assertThat(questions.findById(question.getId()).orElseThrow().getAcceptedAnswerId())
                .isEqualTo(first.getId());
        mvc.perform(get("/community/questions/" + question.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("already has an accepted answer")));
    }

    @Test
    void removingAnAcceptanceOpensaSolvedQuestionAndIsIdempotent() throws Exception {
        CommunityQuestion question = seed(aliceId, "Acceptance removal sample",
                "A question whose acceptance will be taken back.", CommunityCategory.OTHER);
        CommunityAnswer answer = answers.saveAndFlush(CommunityAnswer.post(question.getId(), bobId,
                "An answer that will be accepted and then released.", BASE.plusSeconds(60)));

        mvc.perform(post("/community/questions/" + question.getId() + "/answers/"
                        + answer.getId() + "/accept").with(csrf()).session(login("alice")))
                .andExpect(status().isFound());

        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/community/questions/" + question.getId() + "/acceptance/remove")
                            .with(csrf()).session(login("alice")))
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl("/community/questions/" + question.getId()));
        }

        assertThat(questions.findById(question.getId()).orElseThrow().getAcceptedAnswerId()).isNull();
        // The answer itself is untouched: un-accepting is not a withdrawal.
        assertThat(answers.findById(answer.getId()).orElseThrow().getStatus().name())
                .isEqualTo("VISIBLE");
        mvc.perform(get("/community/questions/" + question.getId()).session(login("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">Open<")));
    }

    @Test
    void removingSomebodyElsesAcceptanceIsANotFound() throws Exception {
        CommunityQuestion question = seed(aliceId, "Acceptance removal ownership sample",
                "A question whose acceptance only its author may remove.",
                CommunityCategory.OTHER);
        CommunityAnswer answer = answers.saveAndFlush(CommunityAnswer.post(question.getId(), bobId,
                "An answer accepted by the question's author.", BASE.plusSeconds(60)));

        mvc.perform(post("/community/questions/" + question.getId() + "/answers/"
                        + answer.getId() + "/accept").with(csrf()).session(login("alice")))
                .andExpect(status().isFound());

        mvc.perform(post("/community/questions/" + question.getId() + "/acceptance/remove")
                        .with(csrf()).session(login("bob")))
                .andExpect(status().isNotFound());

        assertThat(questions.findById(question.getId()).orElseThrow().getAcceptedAnswerId())
                .isEqualTo(answer.getId());
    }

    @Test
    void withdrawingTheAcceptedAnswerOpensTheQuestionAgainInTheSameRequest() throws Exception {
        CommunityQuestion question = seed(aliceId, "Withdrawn acceptance sample",
                "A question whose accepted answer will be withdrawn by its author.",
                CommunityCategory.OTHER);
        CommunityAnswer answer = answers.saveAndFlush(CommunityAnswer.post(question.getId(), bobId,
                "An answer that will be accepted and then withdrawn.", BASE.plusSeconds(60)));

        mvc.perform(post("/community/questions/" + question.getId() + "/answers/"
                        + answer.getId() + "/accept").with(csrf()).session(login("alice")))
                .andExpect(status().isFound());

        mvc.perform(post("/community/answers/" + answer.getId() + "/withdraw").with(csrf())
                        .session(login("bob")))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/community/mine?tab=ANSWERS"));

        // And the redirect has to lead to a page, not to a 400. Nothing else in this file
        // followed one, which is how a hand-written query string drifted from the constant
        // every link is built from: "answers" matched no enum constant, so withdrawing an
        // answer sent its author to an error page. Checking where a redirect points is not
        // the same as checking that it works.
        mvc.perform(get("/community/mine?tab=ANSWERS").session(login("bob")))
                .andExpect(status().isOk());

        // Both rows, in one look: the acceptance is gone and the answer is out of view.
        assertThat(questions.findById(question.getId()).orElseThrow().getAcceptedAnswerId()).isNull();
        assertThat(answers.findById(answer.getId()).orElseThrow().getStatus().name())
                .isEqualTo("WITHDRAWN");

        mvc.perform(get("/community/questions/" + question.getId()).session(login("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("This answer was withdrawn.")))
                .andExpect(content().string(containsString(">Open<")))
                .andExpect(content().string(not(containsString(
                        "An answer that will be accepted and then withdrawn."))));
    }

    @Test
    void editingOrWithdrawingSomebodyElsesAnswerIsANotFound() throws Exception {
        CommunityQuestion question = seed(aliceId, "Answer ownership sample",
                "A question whose answer belongs to somebody else.", CommunityCategory.OTHER);
        CommunityAnswer answer = answers.saveAndFlush(CommunityAnswer.post(question.getId(), bobId,
                "The original text of somebody else's answer.", BASE.plusSeconds(60)));

        mvc.perform(get("/community/answers/" + answer.getId() + "/edit").session(login("alice")))
                .andExpect(status().isNotFound());

        mvc.perform(post("/community/answers/" + answer.getId()).with(csrf())
                        .session(login("alice"))
                        .param("body", "Text somebody else is trying to write into the answer."))
                .andExpect(status().isNotFound());

        mvc.perform(post("/community/answers/" + answer.getId() + "/withdraw").with(csrf())
                        .session(login("alice")))
                .andExpect(status().isNotFound());

        CommunityAnswer untouched = answers.findById(answer.getId()).orElseThrow();
        assertThat(untouched.getBody()).isEqualTo("The original text of somebody else's answer.");
        assertThat(untouched.getStatus().name()).isEqualTo("VISIBLE");
    }

    @Test
    void anAuthorCanEditTheirOwnAnswerAndTheThreadSaysItWasEdited() throws Exception {
        CommunityQuestion question = seed(aliceId, "Answer edit sample",
                "A question whose answer will be corrected.", CommunityCategory.OTHER);
        CommunityAnswer answer = answers.saveAndFlush(CommunityAnswer.post(question.getId(), bobId,
                "The first version of the answer.", BASE.plusSeconds(60)));

        mvc.perform(get("/community/answers/" + answer.getId() + "/edit").session(login("bob")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("The first version of the answer.")))
                .andExpect(content().string(containsString(
                        "action=\"/community/answers/" + answer.getId() + "\"")));

        mvc.perform(post("/community/answers/" + answer.getId()).with(csrf())
                        .session(login("bob"))
                        .param("body", "The corrected version of the answer."))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/community/questions/" + question.getId()));

        // The author is returned to the thread the answer lives on, not to a page of
        // its own, and the answer is marked as edited there.
        mvc.perform(get("/community/questions/" + question.getId()).session(login("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("The corrected version of the answer.")))
                .andExpect(content().string(containsString("edited ")));
    }

    @Test
    void answeringAQuestionThatIsNotPublicIsANotFound() throws Exception {
        CommunityQuestion question = seed(aliceId, "Unanswerable question sample",
                "A question that will be withdrawn before anybody answers it.",
                CommunityCategory.OTHER);
        jdbc.update("UPDATE community_questions SET status = ? WHERE id = ?",
                "WITHDRAWN", question.getId());

        mvc.perform(post("/community/questions/" + question.getId() + "/answers").with(csrf())
                        .session(login("bob"))
                        .param("body", "An answer nobody would be allowed to read."))
                .andExpect(status().isNotFound());

        assertThat(answers.findByQuestionIdOrderByCreatedAtAscIdAsc(question.getId())).isEmpty();
    }

    @Test
    void anAnswerTooShortIsRefusedAndTheTypedTextIsKept() throws Exception {
        CommunityQuestion question = seed(aliceId, "Short answer sample",
                "A question that will be given an answer below the minimum length.",
                CommunityCategory.OTHER);

        mvc.perform(post("/community/questions/" + question.getId() + "/answers").with(csrf())
                        .session(login("bob"))
                        .param("body", "too short"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("between 10 and 4000 characters")))
                // The thread comes back with the box still holding what was typed.
                .andExpect(content().string(containsString("too short")));
    }

    // ------------------------------------------------------------ my answers

    @Test
    void myAnswersTabListsTheReadersOwnAnswersAndNobodyElses() throws Exception {
        CommunityQuestion question = seed(aliceId, "My answers parent sample",
                "A question answered by two different accounts.", CommunityCategory.OTHER);
        answers.saveAndFlush(CommunityAnswer.post(question.getId(), bobId,
                "An answer written by bob and only bob.", BASE.plusSeconds(60)));
        answers.saveAndFlush(CommunityAnswer.post(question.getId(), aliceId,
                "An answer written by alice and only alice.", BASE.plusSeconds(120)));

        mvc.perform(get("/community/mine").param("tab", "ANSWERS").session(login("bob")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your replies and their status.")))
                .andExpect(content().string(containsString("An answer written by bob and only bob.")))
                .andExpect(content().string(not(containsString(
                        "An answer written by alice and only alice."))))
                // The tab that is showing says so without relying on colour.
                .andExpect(content().string(containsString("aria-current=\"page\"")));
    }

    /**
     * The list a leaked question would leak through. An answer to somebody else's
     * withdrawn question shows the reader their own words and nothing about the question:
     * not the title, and not whether it was withdrawn or hidden.
     */
    @Test
    void myAnswersNamesAQuestionOnlyWhereTheReaderMayStillReadIt() throws Exception {
        // Both questions are asked by alice and answered by bob, so bob is neither
        // question's author and has no author's claim on either title.
        CommunityQuestion readable = seed(aliceId, "Readable parent sample",
                "A question that stays public.", CommunityCategory.OTHER);
        CommunityQuestion withdrawn = seed(aliceId, "Withdrawn parent sample",
                "A question its author will withdraw after being answered.",
                CommunityCategory.OTHER);
        answers.saveAndFlush(CommunityAnswer.post(readable.getId(), bobId,
                "An answer on a question that stays public.", BASE.plusSeconds(60)));
        answers.saveAndFlush(CommunityAnswer.post(withdrawn.getId(), bobId,
                "An answer on a question that will be withdrawn.", BASE.plusSeconds(120)));

        jdbc.update("UPDATE community_questions SET status = ? WHERE id = ?",
                "WITHDRAWN", withdrawn.getId());

        mvc.perform(get("/community/mine").param("tab", "ANSWERS").session(login("bob")))
                .andExpect(status().isOk())
                // Bob's own answer text is his to read, on both.
                .andExpect(content().string(containsString(
                        "An answer on a question that will be withdrawn.")))
                // The readable parent is named and linked.
                .andExpect(content().string(containsString("Readable parent sample")))
                .andExpect(content().string(containsString(
                        "href=\"/community/questions/" + readable.getId() + "\"")))
                // The withdrawn one is named nowhere and linked nowhere, and the page does
                // not say which of the two states it is in.
                .andExpect(content().string(not(containsString("Withdrawn parent sample"))))
                .andExpect(content().string(containsString("Not available")))
                .andExpect(content().string(not(containsString(
                        "href=\"/community/questions/" + withdrawn.getId() + "\""))));
    }

    @Test
    void myAnswersExplainsItselfWhenTheAccountHasAnsweredNothing() throws Exception {
        mvc.perform(get("/community/mine").param("tab", "ANSWERS").session(login("quiet")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("You have not answered anything yet")));
    }

    @Test
    void anUnrecognisedTabIsRefusedRatherThanSilentlyShowingQuestions() throws Exception {
        mvc.perform(get("/community/mine").param("tab", "nonsense").session(login("alice")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void thePagerCarriesTheAnswersTabForward() throws Exception {
        CommunityQuestion question = seed(aliceId, "Answers paging parent",
                "A question that will carry eleven answers.", CommunityCategory.OTHER);
        for (int index = 0; index < 11; index++) {
            answers.saveAndFlush(CommunityAnswer.post(question.getId(), pagerId,
                    "A paging sample answer, number " + index + ".", BASE.plusSeconds(60L * index)));
        }

        mvc.perform(get("/community/mine").param("tab", "ANSWERS").param("size", "10")
                        .session(login("pager")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("11 answers")))
                .andExpect(content().string(containsString("tab=ANSWERS")));
    }

    // ------------------------------------------------------------- helpers

    private Long idFrom(String redirectUrl) {
        return Long.valueOf(redirectUrl.substring(redirectUrl.lastIndexOf('/') + 1));
    }

    private static int countOf(String haystack, String needle) {
        int count = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            count++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return count;
    }

    /** Seeds a visible question with a timestamp later than every question before it. */
    private CommunityQuestion seed(
            Long authorId, String title, String body, CommunityCategory category) {
        Instant createdAt = BASE.plusSeconds(60L * (++seeded));
        return questions.saveAndFlush(
                CommunityQuestion.ask(authorId, title, body, category, createdAt));
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
