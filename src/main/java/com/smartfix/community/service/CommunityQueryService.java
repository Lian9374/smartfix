package com.smartfix.community.service;

import com.smartfix.common.exception.InputValidationException;
import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.community.config.CommunityProperties;
import com.smartfix.community.domain.CommunityAnswer;
import com.smartfix.community.domain.CommunityCategory;
import com.smartfix.community.domain.CommunityQuestion;
import com.smartfix.community.dto.CommunityAnswerResponse;
import com.smartfix.community.dto.CommunityQuestionDetailResponse;
import com.smartfix.community.dto.CommunityQuestionSummaryResponse;
import com.smartfix.community.dto.MyAnswerResponse;
import com.smartfix.community.dto.OwnAnswerResponse;
import com.smartfix.community.dto.QuestionFilter;
import com.smartfix.community.repository.CommunityAnswerRepository;
import com.smartfix.community.repository.CommunityQuestionRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read side of the community board: the list, the search, "my questions" and the detail.
 *
 * <h2>One visibility predicate, in one place</h2>
 *
 * <p>Plan section 6.8 (R13) requires the same {@code status = 'VISIBLE'} rule in the list,
 * the detail read, the search and the count. Three of those four are inside
 * {@code CommunityQuestionRepository}'s query text and cannot be omitted by a caller; the
 * fourth is {@link #findQuestionDetail}, the only read that has to look at a row that is
 * not public, and it states the rule explicitly below. There is no other path to a
 * question in this module.</p>
 *
 * <h2>Case-insensitive search that behaves the same on both databases</h2>
 *
 * <p>The search compares {@code LOWER(column) LIKE :pattern}, with the term lowercased in
 * Java and the wildcards escaped in Java, rather than using PostgreSQL's {@code ILIKE}.
 * {@code ILIKE} is not portable to the H2 database every test in this project runs on, so
 * a search written with it would be verified nowhere. Escaping is explicit rather than
 * left to chance: a term containing {@code %} or {@code _} is a literal search for those
 * characters, not a wildcard, because a user typing a percent sign means a percent sign.</p>
 *
 * <p>Whether the comparison is accent-insensitive or case-folds beyond ASCII depends on
 * the database collation and is not claimed either way.</p>
 */
@Service
@Transactional(readOnly = true)
public class CommunityQueryService {

    /** How much of a body the list shows before it stops. */
    private static final int EXCERPT_LENGTH = 160;
    private static final String EXCERPT_ELLIPSIS = "…";

    /**
     * The character that makes the next character in a {@code LIKE} pattern literal.
     *
     * <p>{@code !} rather than the customary backslash, because a backslash inside a JPQL
     * string literal has to be doubled and the doubling is easy to get wrong; {@code !}
     * needs no escaping anywhere in the chain.</p>
     */
    private static final char LIKE_ESCAPE = '!';

    private final CommunityQuestionRepository questions;
    private final CommunityAnswerRepository answers;
    private final CommunityAccessGuard access;
    private final CommunityProperties properties;

    public CommunityQueryService(
            CommunityQuestionRepository questions,
            CommunityAnswerRepository answers,
            CommunityAccessGuard access,
            CommunityProperties properties) {
        this.questions = questions;
        this.answers = answers;
        this.access = access;
        this.properties = properties;
    }

    /**
     * The public board: newest first, optionally filtered by topic, by tab and by text.
     *
     * @param category the topic to restrict to, or {@code null} for all of them
     * @param filter   which tab; {@code null} is treated as {@link QuestionFilter#LATEST}
     * @param query    the search term, or {@code null} or blank for no search
     * @param page     the page number; a negative value is read as the first page
     * @param size     the page size; a non-positive value takes the configured default
     * @throws InputValidationException when the search term is longer than the limit
     */
    public Page<CommunityQuestionSummaryResponse> browse(
            Long actorUserId,
            CommunityCategory category,
            QuestionFilter filter,
            String query,
            int page,
            int size) {
        access.requireActiveUser(actorUserId);

        // "No category chosen" is expressed as all five rather than as a null parameter.
        // A nullable category would need (:category IS NULL OR q.category = :category),
        // and PostgreSQL cannot infer a type for a parameter used only in ? IS NULL. An
        // IN over every constant is a plain comparison that both databases run alike.
        Collection<CommunityCategory> categories = category == null
                ? EnumSet.allOf(CommunityCategory.class)
                : EnumSet.of(category);
        String pattern = likePattern(query);
        Pageable pageable = pageRequest(page, size);

        Page<CommunityQuestion> found = switch (filter == null ? QuestionFilter.LATEST : filter) {
            case UNANSWERED -> questions.searchVisibleUnanswered(categories, pattern, pageable);
            case SOLVED -> questions.searchVisibleSolved(categories, pattern, pageable);
            case LATEST -> questions.searchVisible(categories, pattern, pageable);
        };
        return found.map(this::toSummary);
    }

    /**
     * Every question the caller asked, newest first, including the ones they withdrew.
     *
     * <p>The only read here that does not filter on {@code VISIBLE}, and deliberately so:
     * a page that hid an author's own withdrawn question would leave them unable to
     * confirm it had been withdrawn.</p>
     */
    public Page<CommunityQuestionSummaryResponse> listMyQuestions(Long actorUserId, int page, int size) {
        access.requireActiveUser(actorUserId);
        return questions.findByAuthorId(actorUserId, pageRequest(page, size)).map(this::toSummary);
    }

    /**
     * One question and its thread.
     *
     * <p>A question the caller may not read answers {@link ResourceNotFoundException} - a
     * 404 - even though it exists. Plan section 13.4 requires that: distinguishing "not
     * there" from "not yours" would turn the response into a way of discovering other
     * people's question ids.</p>
     *
     * <p>Two callers may read a question that is not public: its author, who is told the
     * question is no longer public rather than being shown it as though it were, and
     * (later) an administrator, whose view is the moderation screen and not this one.</p>
     */
    public CommunityQuestionDetailResponse findQuestionDetail(Long actorUserId, Long questionId) {
        access.requireActiveUser(actorUserId);
        if (questionId == null) {
            throw new ResourceNotFoundException("Community content not found.");
        }
        CommunityQuestion question = questions.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Community content not found."));
        if (!question.isPubliclyVisible() && !question.getAuthorId().equals(actorUserId)) {
            throw new ResourceNotFoundException("Community content not found.");
        }
        return toDetail(question);
    }

    /**
     * One of the caller's own answers, for the edit form.
     *
     * <p>Ownership and visibility are the form's whole eligibility rule: an answer somebody
     * else wrote is a 404 rather than a form, and so is one the caller wrote but can no
     * longer change. Rendering an editor whose save would be refused is the failure this
     * avoids; the entity enforces the same rule again when the form is submitted, so a
     * crafted POST cannot get past it.</p>
     *
     * @throws ResourceNotFoundException when there is no such answer, the caller did not
     *         write it, or it is no longer publicly visible
     */
    public OwnAnswerResponse findOwnAnswer(Long actorUserId, Long answerId) {
        access.requireActiveUser(actorUserId);
        if (answerId == null) {
            throw new ResourceNotFoundException("Community content not found.");
        }
        CommunityAnswer answer = answers.findByIdAndAuthorId(answerId, actorUserId)
                .filter(CommunityAnswer::isPubliclyVisible)
                .orElseThrow(() -> new ResourceNotFoundException("Community content not found."));
        return new OwnAnswerResponse(answer.getId(), answer.getQuestionId(), answer.getBody());
    }

    /**
     * The thread a publicly visible answer lives in.
     *
     * <p>Read for one purpose: the report route names an answer and nothing else, and
     * the reporter has to be returned to the page they reported from. The target could
     * not be taken from the submitted form - a redirect destination that arrived in a
     * request body would be a second spelling of something the service already
     * decided.</p>
     *
     * <p>Both the answer and its question must be publicly visible, which is exactly
     * the rule {@code CommunityModerationService.reportAnswer} applies before it
     * accepts a report of one. Stating it here as well is deliberate rather than
     * duplicated by accident: this method answers "may this reader be sent to that
     * thread", and answering it with a weaker rule would send a reporter to a page that
     * renders a 404.</p>
     *
     * @throws ResourceNotFoundException when there is no such answer, or either the
     *         answer or its question is not public
     */
    public Long findThreadIdOfAnswer(Long actorUserId, Long answerId) {
        access.requireActiveUser(actorUserId);
        if (answerId == null) {
            throw new ResourceNotFoundException("Community content not found.");
        }
        CommunityAnswer answer = answers.findById(answerId)
                .filter(CommunityAnswer::isPubliclyVisible)
                .orElseThrow(() -> new ResourceNotFoundException("Community content not found."));
        return questions.findById(answer.getQuestionId())
                .filter(CommunityQuestion::isPubliclyVisible)
                .map(CommunityQuestion::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Community content not found."));
    }

    /**
     * Every answer the caller wrote, newest first, including the ones they withdrew.
     *
     * <p>Each row carries its question's title only where the caller may read that
     * question, which is the detail page's own predicate: the question is public, or the
     * caller wrote it. Nothing else about the question travels - not its body, not its
     * status, not its id beyond the one the caller's own answer already names - so a
     * withdrawn question cannot be reconstructed through this list.</p>
     *
     * @see MyAnswerResponse for why the title is nullable rather than replaced by a reason
     */
    public Page<MyAnswerResponse> listMyAnswers(Long actorUserId, int page, int size) {
        access.requireActiveUser(actorUserId);
        Page<CommunityAnswer> found = answers.findByAuthorId(actorUserId, pageRequest(page, size));
        Map<Long, CommunityQuestion> parents = loadParents(found.getContent());
        return found.map(answer -> toMyAnswer(answer, parents.get(answer.getQuestionId()), actorUserId));
    }

    /**
     * The questions behind one page of answers, in one query rather than one per row.
     *
     * <p>A missing entry is not expected - an answer's question is held by a foreign key -
     * but it is treated as "not readable" rather than as an error, because the only
     * consequence of an absent parent here is a row that names no question.</p>
     */
    private Map<Long, CommunityQuestion> loadParents(List<CommunityAnswer> page) {
        Set<Long> questionIds = page.stream()
                .map(CommunityAnswer::getQuestionId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, CommunityQuestion> byId = new HashMap<>();
        for (CommunityQuestion question : questions.findAllById(questionIds)) {
            byId.put(question.getId(), question);
        }
        return byId;
    }

    private MyAnswerResponse toMyAnswer(
            CommunityAnswer answer, CommunityQuestion parent, Long actorUserId) {
        boolean parentReadable = parent != null
                && (parent.isPubliclyVisible() || parent.getAuthorId().equals(actorUserId));
        return new MyAnswerResponse(
                answer.getId(),
                answer.getQuestionId(),
                parentReadable ? parent.getTitle() : null,
                // The caller's own text, shown even when the answer itself is withdrawn or
                // hidden: plan section 6.7 (R14) lets an author see their own non-public
                // content, and the body belongs to the reader either way.
                answer.getBody(),
                answer.getStatus(),
                answer.getCreatedAt(),
                answer.getUpdatedAt(),
                answer.getUpdatedAt() != null && answer.getUpdatedAt().isAfter(answer.getCreatedAt()));
    }

    /**
     * A question and its thread, with the accepted answer first.
     *
     * <h2>Pinned, and only once</h2>
     *
     * <p>Plan section 17.1 puts the accepted answer at the top of the list and the rest in
     * chronological order. Those two rules conflict for the accepted answer itself, and
     * the resolution here is to move it rather than to copy it: the thread is read oldest
     * first as one ordered query, the accepted answer is lifted out of that order to the
     * front, and every other answer keeps the position it had. The accepted answer
     * therefore appears exactly once, at the top.</p>
     *
     * <p>Copying it would be the tempting alternative and the worse one: the page would
     * show one answer twice, its two copies could diverge in what they offer, and the
     * "Answers (n)" count would disagree with what a reader can count.</p>
     */
    private CommunityQuestionDetailResponse toDetail(CommunityQuestion question) {
        List<CommunityAnswer> thread = answers
                .findByQuestionIdOrderByCreatedAtAscIdAsc(question.getId());
        Long acceptedAnswerId = question.getAcceptedAnswerId();

        List<CommunityAnswerResponse> ordered = new ArrayList<>(thread.size());
        thread.stream()
                .filter(answer -> answer.getId().equals(acceptedAnswerId))
                .findFirst()
                .ifPresent(answer -> ordered.add(toAnswer(answer, acceptedAnswerId)));
        thread.stream()
                .filter(answer -> !answer.getId().equals(acceptedAnswerId))
                .forEach(answer -> ordered.add(toAnswer(answer, acceptedAnswerId)));

        return new CommunityQuestionDetailResponse(
                question.getId(),
                question.getTitle(),
                question.getBody(),
                question.getCategory(),
                question.getAuthorId(),
                question.getStatus(),
                question.isSolved(),
                question.getAcceptedAnswerId(),
                question.getCreatedAt(),
                question.getUpdatedAt(),
                question.isEdited(),
                ordered);
    }

    private CommunityAnswerResponse toAnswer(CommunityAnswer answer, Long acceptedAnswerId) {
        return new CommunityAnswerResponse(
                answer.getId(),
                answer.getQuestionId(),
                answer.getAuthorId(),
                // Withheld rather than omitted: the thread keeps its shape, and a body
                // that is not in the response cannot be rendered by a template that
                // forgot to check the status.
                answer.isPubliclyVisible() ? answer.getBody() : null,
                answer.getStatus(),
                answer.getId().equals(acceptedAnswerId),
                answer.getCreatedAt(),
                answer.getUpdatedAt(),
                answer.getUpdatedAt() != null && answer.getUpdatedAt().isAfter(answer.getCreatedAt()));
    }

    private CommunityQuestionSummaryResponse toSummary(CommunityQuestion question) {
        return new CommunityQuestionSummaryResponse(
                question.getId(),
                question.getTitle(),
                excerptOf(question.getBody()),
                question.getCategory(),
                question.getAuthorId(),
                question.getStatus(),
                question.isSolved(),
                question.getCreatedAt(),
                question.getUpdatedAt(),
                question.isEdited());
    }

    /** Collapses runs of whitespace so a multi-line body still reads as one line. */
    private static String excerptOf(String body) {
        if (body == null) {
            return "";
        }
        String flattened = body.replaceAll("\\s+", " ").trim();
        if (flattened.length() <= EXCERPT_LENGTH) {
            return flattened;
        }
        return flattened.substring(0, EXCERPT_LENGTH).stripTrailing() + EXCERPT_ELLIPSIS;
    }

    /**
     * Turns a search term into a {@code LIKE} pattern, escaping the wildcards.
     *
     * <p>A blank term becomes {@code "%"}, which matches every non-null title: the query
     * then has no search clause in effect without needing a second query text, so the
     * "search" path and the "no search" path cannot drift apart.</p>
     *
     * @throws InputValidationException when the trimmed term exceeds the length limit
     */
    static String likePattern(String query) {
        String term = query == null ? "" : query.trim();
        if (term.isEmpty()) {
            return "%";
        }
        if (term.length() > CommunityQuestion.SEARCH_TERM_MAX_LENGTH) {
            throw new InputValidationException("Search terms must be at most "
                    + CommunityQuestion.SEARCH_TERM_MAX_LENGTH + " characters.");
        }
        StringBuilder pattern = new StringBuilder(term.length() + 2).append('%');
        for (char character : term.toLowerCase(Locale.ROOT).toCharArray()) {
            if (character == LIKE_ESCAPE || character == '%' || character == '_') {
                pattern.append(LIKE_ESCAPE);
            }
            pattern.append(character);
        }
        return pattern.append('%').toString();
    }

    /**
     * Paging with the project's conventions: a negative page reads as the first page,
     * a non-positive or oversized size takes the configured default or ceiling, and the
     * sort is always {@code created_at DESC, id DESC}.
     *
     * <p>The id is part of the sort on purpose. Two questions can share a timestamp, and
     * without a tie-breaker the database is free to order them differently on each page
     * request, which shows up as a row appearing twice and another never appearing at
     * all. This matches {@code RequestQueryService}.</p>
     */
    private Pageable pageRequest(int page, int size) {
        return PageRequest.of(
                Math.max(page, 0),
                properties.getPage().resolveSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }
}
