package com.smartfix.community.service;

import com.smartfix.common.exception.InputValidationException;
import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.community.config.CommunityProperties;
import com.smartfix.community.domain.CommunityAnswer;
import com.smartfix.community.domain.CommunityCategory;
import com.smartfix.community.domain.CommunityContentStatus;
import com.smartfix.community.domain.CommunityQuestion;
import com.smartfix.community.dto.CommunityAnswerResponse;
import com.smartfix.community.dto.CommunityQuestionDetailResponse;
import com.smartfix.community.dto.MyAnswerResponse;
import com.smartfix.community.dto.QuestionFilter;
import com.smartfix.community.repository.CommunityAnswerRepository;
import com.smartfix.community.repository.CommunityQuestionRepository;
import com.smartfix.user.dto.UserAccessResponse;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * What the query service decides before any query runs: which predicate, which page, and
 * who is allowed to see a question that is not public.
 *
 * <p>The queries themselves are checked against a real schema in
 * {@code CommunityQuestionRepositoryTest}; this test checks the arguments handed to them
 * and the mapping that comes back, which a database cannot report on.</p>
 */
class CommunityQueryServiceTest {

    private static final Long ACTOR = 7L;
    private static final Long AUTHOR = 7L;
    private static final Long STRANGER = 8L;

    private CommunityQuestionRepository questions;
    private CommunityAnswerRepository answers;
    private CommunityAccessGuard access;
    private CommunityQueryService service;

    @BeforeEach
    void setUp() {
        questions = mock(CommunityQuestionRepository.class);
        answers = mock(CommunityAnswerRepository.class);
        access = mock(CommunityAccessGuard.class);
        service = new CommunityQueryService(questions, answers, access, new CommunityProperties());

        when(access.requireActiveUser(anyLong()))
                .thenReturn(new UserAccessResponse(ACTOR, Role.REQUESTER, AccountStatus.ACTIVE, 0L));
        when(questions.searchVisible(any(), anyString(), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(questions.searchVisibleUnanswered(any(), anyString(), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(questions.searchVisibleSolved(any(), anyString(), any(Pageable.class)))
                .thenReturn(Page.empty());
        // any() rather than anyLong(): a question built in a unit test has no id yet,
        // because nothing has saved it. The id is not what these tests are about.
        when(answers.findByQuestionIdOrderByCreatedAtAscIdAsc(any())).thenReturn(List.of());
        when(answers.findByAuthorId(anyLong(), any(Pageable.class))).thenReturn(Page.empty());
    }

    @Test
    void noTopicFilterIsPassedAsEveryTopicRatherThanAsNull() {
        service.browse(ACTOR, null, QuestionFilter.LATEST, null, 0, 10);

        assertThat(capturedCategories())
                .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(CommunityCategory.class));
    }

    @Test
    void aChosenTopicIsPassedAsJustThatOne() {
        service.browse(ACTOR, CommunityCategory.NETWORK, QuestionFilter.LATEST, null, 0, 10);

        assertThat(capturedCategories()).containsExactly(CommunityCategory.NETWORK);
    }

    @Test
    void eachTabSelectsItsOwnQuery() {
        service.browse(ACTOR, null, QuestionFilter.UNANSWERED, null, 0, 10);
        service.browse(ACTOR, null, QuestionFilter.SOLVED, null, 0, 10);

        verify(questions).searchVisibleUnanswered(any(), anyString(), any(Pageable.class));
        verify(questions).searchVisibleSolved(any(), anyString(), any(Pageable.class));
        // The default tab must not have been used as a stand-in for either of them.
        verify(questions, never()).searchVisible(any(), anyString(), any(Pageable.class));
    }

    @Test
    void aNullTabIsTheDefaultRatherThanAnError() {
        service.browse(ACTOR, null, null, null, 0, 10);

        verify(questions).searchVisible(any(), anyString(), any(Pageable.class));
    }

    @Test
    void pageSizesFollowTheConfiguredDefaultAndCeiling() {
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);

        service.browse(ACTOR, null, QuestionFilter.LATEST, null, -3, 0);
        service.browse(ACTOR, null, QuestionFilter.LATEST, null, 0, 999);
        verify(questions, times(2)).searchVisible(any(), anyString(), page.capture());

        List<Pageable> captured = page.getAllValues();
        assertThat(captured.get(0).getPageNumber()).isZero();
        assertThat(captured.get(0).getPageSize()).isEqualTo(10);
        assertThat(captured.get(1).getPageNumber()).isZero();
        assertThat(captured.get(1).getPageSize()).isEqualTo(50);
    }

    @Test
    void theSortIsNewestFirstWithAnIdTieBreak() {
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        service.browse(ACTOR, null, QuestionFilter.LATEST, null, 0, 10);
        verify(questions).searchVisible(any(), anyString(), page.capture());

        assertThat(page.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    @Test
    void aSearchTermLongerThanTheLimitIsRejectedBeforeAnyQuery() {
        assertThatThrownBy(() -> service.browse(
                ACTOR, null, QuestionFilter.LATEST, "x".repeat(101), 0, 10))
                .isInstanceOf(InputValidationException.class);
        verifyNoInteractions(questions);
    }

    @Test
    void aBlankSearchBecomesAPatternThatMatchesEverything() {
        assertThat(CommunityQueryService.likePattern(null)).isEqualTo("%");
        assertThat(CommunityQueryService.likePattern("   ")).isEqualTo("%");
        assertThat(CommunityQueryService.likePattern(" wifi ")).isEqualTo("%wifi%");
    }

    @Test
    void wildcardsInASearchTermAreSearchedForLiterally() {
        // A user typing a percent sign means a percent sign, not "match anything". Without
        // the escape this pattern would return every question, which looks like a search
        // that ignored what was typed.
        assertThat(CommunityQueryService.likePattern("50%")).isEqualTo("%50!%%");
        assertThat(CommunityQueryService.likePattern("a_b")).isEqualTo("%a!_b%");
        // The escape character itself has to survive as a literal.
        assertThat(CommunityQueryService.likePattern("ping!")).isEqualTo("%ping!!%");
    }

    @Test
    void aQuestionThatIsNotPublicIsNotFoundByAnyoneElse() {
        CommunityQuestion withdrawn = question(AUTHOR, CommunityContentStatus.WITHDRAWN);
        when(questions.findById(1L)).thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> service.findQuestionDetail(STRANGER, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aQuestionThatIsNotPublicStillOpensForItsOwnAuthor() {
        CommunityQuestion withdrawn = question(AUTHOR, CommunityContentStatus.WITHDRAWN);
        when(questions.findById(1L)).thenReturn(Optional.of(withdrawn));

        CommunityQuestionDetailResponse detail = service.findQuestionDetail(AUTHOR, 1L);

        assertThat(detail.status()).isEqualTo(CommunityContentStatus.WITHDRAWN);
        assertThat(detail.title()).isEqualTo("Wifi drops in the library");
    }

    @Test
    void aMissingQuestionIsNotFound() {
        when(questions.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findQuestionDetail(ACTOR, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void anAnswersBodyIsWithheldUnlessItIsPubliclyVisible() {
        CommunityQuestion question = question(AUTHOR, CommunityContentStatus.VISIBLE);
        CommunityAnswer visible = answer(20L, CommunityContentStatus.VISIBLE);
        CommunityAnswer withdrawn = answer(21L, CommunityContentStatus.WITHDRAWN);
        when(questions.findById(1L)).thenReturn(Optional.of(question));
        when(answers.findByQuestionIdOrderByCreatedAtAscIdAsc(any()))
                .thenReturn(List.of(visible, withdrawn));

        CommunityQuestionDetailResponse detail = service.findQuestionDetail(ACTOR, 1L);

        assertThat(detail.answers()).hasSize(2);
        assertThat(detail.answers().get(0).body()).isEqualTo("Try a different cable.");
        // The row survives so the thread keeps its shape; only the text is withheld.
        assertThat(detail.answers().get(1).body()).isNull();
        assertThat(detail.answers().get(1).status()).isEqualTo(CommunityContentStatus.WITHDRAWN);
    }

    /**
     * Pinned first, and pinned once. The two rules - "the accepted answer is at the top"
     * and "the rest are in chronological order" - conflict for the accepted answer itself,
     * and the resolution is to move it rather than to copy it. A copy would show one answer
     * twice, and its two renderings could disagree.
     */
    @Test
    void theAcceptedAnswerIsMovedToTheFrontAndAppearsOnlyOnce() {
        CommunityQuestion question = question(AUTHOR, CommunityContentStatus.VISIBLE);
        ReflectionTestUtils.setField(question, "acceptedAnswerId", 21L);
        CommunityAnswer olderAccepted =
                answer(21L, CommunityContentStatus.VISIBLE);
        CommunityAnswer newer = answer(22L, CommunityContentStatus.VISIBLE);
        when(questions.findById(1L)).thenReturn(Optional.of(question));
        // Read oldest first, as the query does.
        when(answers.findByQuestionIdOrderByCreatedAtAscIdAsc(any()))
                .thenReturn(List.of(olderAccepted, newer));

        CommunityQuestionDetailResponse detail = service.findQuestionDetail(ACTOR, 1L);

        assertThat(detail.answers()).extracting(CommunityAnswerResponse::id)
                .containsExactly(21L, 22L);
        assertThat(detail.answers()).extracting(CommunityAnswerResponse::accepted)
                .containsExactly(true, false);
        assertThat(detail.solved()).isTrue();
    }

    /** A question with no accepted answer keeps the plain chronological order. */
    @Test
    void aQuestionWithNoAcceptedAnswerKeepsTheThreadInOrder() {
        CommunityQuestion question = question(AUTHOR, CommunityContentStatus.VISIBLE);
        CommunityAnswer first = answer(20L, CommunityContentStatus.VISIBLE);
        CommunityAnswer second = answer(21L, CommunityContentStatus.VISIBLE);
        when(questions.findById(1L)).thenReturn(Optional.of(question));
        when(answers.findByQuestionIdOrderByCreatedAtAscIdAsc(any()))
                .thenReturn(List.of(first, second));

        CommunityQuestionDetailResponse detail = service.findQuestionDetail(ACTOR, 1L);

        assertThat(detail.answers()).extracting(CommunityAnswerResponse::id)
                .containsExactly(20L, 21L);
        assertThat(detail.answers()).allMatch(response -> !response.accepted());
        assertThat(detail.solved()).isFalse();
    }

    // ----------------------------------------------------------- my answers

    @Test
    void myAnswersNamesTheQuestionWhereTheReaderMayReadIt() {
        CommunityQuestion readable = question(AUTHOR, CommunityContentStatus.VISIBLE);
        CommunityAnswer row = answer(20L, CommunityContentStatus.VISIBLE);
        when(answers.findByAuthorId(eq(STRANGER), any(Pageable.class))).thenReturn(page(row));
        when(questions.findAllById(any())).thenReturn(List.of(readable));

        Page<MyAnswerResponse> mine = service.listMyAnswers(STRANGER, 0, 10);

        assertThat(mine.getContent()).hasSize(1);
        assertThat(mine.getContent().get(0).questionTitle())
                .isEqualTo("Wifi drops in the library");
        assertThat(mine.getContent().get(0).questionId()).isEqualTo(1L);
    }

    /**
     * The list a leaked question would leak through. When the caller may not read the
     * question - it is somebody else's and it is not public - the title is null rather than
     * replaced by a reason, so the row cannot be used to tell "withdrawn" from "hidden by a
     * moderator" either. The caller's own answer text still travels: it is their writing.
     */
    @Test
    void myAnswersWithholdsTheQuestionTitleWhereTheReaderMayNotReadIt() {
        CommunityQuestion withdrawn = question(AUTHOR, CommunityContentStatus.WITHDRAWN);
        CommunityAnswer row = answer(20L, CommunityContentStatus.WITHDRAWN);
        when(answers.findByAuthorId(eq(STRANGER), any(Pageable.class))).thenReturn(page(row));
        when(questions.findAllById(any())).thenReturn(List.of(withdrawn));

        Page<MyAnswerResponse> mine = service.listMyAnswers(STRANGER, 0, 10);

        assertThat(mine.getContent().get(0).questionTitle()).isNull();
        assertThat(mine.getContent().get(0).questionId()).isEqualTo(1L);
        assertThat(mine.getContent().get(0).body()).isEqualTo("Try a different cable.");
    }

    @Test
    void myAnswersNamesAnAuthorTheirOwnQuestionEvenWhenItIsNotPublic() {
        CommunityQuestion own = question(STRANGER, CommunityContentStatus.WITHDRAWN);
        CommunityAnswer row = answer(20L, CommunityContentStatus.VISIBLE);
        when(answers.findByAuthorId(eq(STRANGER), any(Pageable.class))).thenReturn(page(row));
        when(questions.findAllById(any())).thenReturn(List.of(own));

        assertThat(service.listMyAnswers(STRANGER, 0, 10).getContent().get(0).questionTitle())
                .isEqualTo("Wifi drops in the library");
    }

    @Test
    void myAnswersReadsEveryParentInOneQueryRatherThanOnePerRow() {
        CommunityAnswer first = answer(20L, CommunityContentStatus.VISIBLE);
        CommunityAnswer second = answer(21L, CommunityContentStatus.VISIBLE);
        when(answers.findByAuthorId(eq(STRANGER), any(Pageable.class)))
                .thenReturn(page(first, second));

        service.listMyAnswers(STRANGER, 0, 10);

        verify(questions, times(1)).findAllById(any());
    }

    /** A page of answers, paged and sorted the way every other list here is. */
    @Test
    void myAnswersPagesNewestFirstWithTheSameCeilingAsEveryOtherList() {
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);

        service.listMyAnswers(STRANGER, -2, 9999);

        verify(answers).findByAuthorId(eq(STRANGER), page.capture());
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(page.getValue().getPageSize()).isEqualTo(50);
        assertThat(page.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    private Page<CommunityAnswer> page(CommunityAnswer... rows) {
        return new PageImpl<>(List.of(rows));
    }

    /** The category collection handed to the visible-list query. */
    @SuppressWarnings("unchecked")
    private Collection<CommunityCategory> capturedCategories() {
        ArgumentCaptor<Collection<CommunityCategory>> captor =
                ArgumentCaptor.forClass(Collection.class);
        verify(questions).searchVisible(captor.capture(), anyString(), any(Pageable.class));
        return captor.getValue();
    }

    private CommunityQuestion question(Long authorId, CommunityContentStatus status) {
        CommunityQuestion question = CommunityQuestion.ask(
                authorId,
                "Wifi drops in the library",
                "The network drops every few minutes near the reading room.",
                CommunityCategory.NETWORK,
                Instant.parse("2026-09-20T08:00:00Z"));
        if (status == CommunityContentStatus.WITHDRAWN) {
            question.withdraw(Instant.parse("2026-09-20T09:00:00Z"));
        }
        // The id the database would have assigned. The answer fixtures below point at
        // question 1, and the "my answers" list looks its parents up by that id, so a
        // question without one would be filed under null and found by nobody.
        ReflectionTestUtils.setField(question, "id", 1L);
        return question;
    }

    private CommunityAnswer answer(Long id, CommunityContentStatus status) {
        CommunityAnswer answer = mock(CommunityAnswer.class);
        when(answer.getId()).thenReturn(id);
        when(answer.getQuestionId()).thenReturn(1L);
        when(answer.getAuthorId()).thenReturn(STRANGER);
        when(answer.getStatus()).thenReturn(status);
        when(answer.isPubliclyVisible()).thenReturn(status.isPubliclyVisible());
        when(answer.getBody()).thenReturn("Try a different cable.");
        when(answer.getCreatedAt()).thenReturn(Instant.parse("2026-09-20T10:00:00Z"));
        when(answer.getUpdatedAt()).thenReturn(Instant.parse("2026-09-20T10:00:00Z"));
        return answer;
    }
}
