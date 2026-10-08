package com.smartfix.community.repository;

import com.smartfix.community.domain.CommunityCategory;
import com.smartfix.community.domain.CommunityQuestion;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

/**
 * Persistence owned by the community module for the question aggregate root.
 *
 * <h2>Why the public reads hard-code {@code status = VISIBLE}</h2>
 *
 * <p>Visibility is a rule, not a parameter. Plan section 6.8 (R13) requires the same
 * {@code status = 'VISIBLE'} predicate in the list, the detail read, the search and the
 * count; if it were a bound argument, one caller could forget it and publish hidden
 * content without any type error to catch the mistake. Keeping it inside the query text
 * makes that impossible to get wrong from the outside, and confines the four places the
 * contract asks to keep consistent to the two methods below plus the service's detail
 * read.</p>
 *
 * <h2>Why the filters are enumerated rather than optional</h2>
 *
 * <p>The obvious shape - {@code (:category IS NULL OR q.category = :category)} - is
 * avoided on purpose. PostgreSQL cannot infer a type for a bare parameter used only in
 * {@code ? IS NULL}, and this project's tests run on H2, so such a query could pass every
 * test here and still fail on the deployed database. Instead an unfiltered category is
 * expressed as "any of the five", which is a plain {@code IN} that both databases
 * execute identically. The same reasoning covers the text pattern: it is never null,
 * because "no search term" binds {@code \%}.</p>
 */
public interface CommunityQuestionRepository extends JpaRepository<CommunityQuestion, Long> {

    /**
     * Latest-first public questions, optionally narrowed to some categories.
     *
     * @param categories the categories to include; pass every constant to filter nothing
     * @param pattern    an already-lowercased {@code LIKE} pattern, with {@code !} as its
     *                   escape character, matched against the title and the body
     * @param pageable   paging and the stable sort
     */
    @Query(value = """
            SELECT q FROM CommunityQuestion q
            WHERE q.status = com.smartfix.community.domain.CommunityContentStatus.VISIBLE
              AND q.category IN :categories
              AND (LOWER(q.title) LIKE :pattern ESCAPE '!' OR LOWER(q.body) LIKE :pattern ESCAPE '!')
            """,
            countQuery = """
            SELECT COUNT(q) FROM CommunityQuestion q
            WHERE q.status = com.smartfix.community.domain.CommunityContentStatus.VISIBLE
              AND q.category IN :categories
              AND (LOWER(q.title) LIKE :pattern ESCAPE '!' OR LOWER(q.body) LIKE :pattern ESCAPE '!')
            """)
    Page<CommunityQuestion> searchVisible(
            @Param("categories") Collection<CommunityCategory> categories,
            @Param("pattern") String pattern,
            Pageable pageable);

    /**
     * Public questions nobody has answered yet.
     *
     * <p>"Unanswered" means no accepted answer, not zero replies - a question can carry
     * several answers and still be open, and it is the asking user's unanswered question
     * that this filter is meant to surface. Plan section 6.6 states the same rule.</p>
     */
    @Query(value = """
            SELECT q FROM CommunityQuestion q
            WHERE q.status = com.smartfix.community.domain.CommunityContentStatus.VISIBLE
              AND q.acceptedAnswerId IS NULL
              AND q.category IN :categories
              AND (LOWER(q.title) LIKE :pattern ESCAPE '!' OR LOWER(q.body) LIKE :pattern ESCAPE '!')
            """,
            countQuery = """
            SELECT COUNT(q) FROM CommunityQuestion q
            WHERE q.status = com.smartfix.community.domain.CommunityContentStatus.VISIBLE
              AND q.acceptedAnswerId IS NULL
              AND q.category IN :categories
              AND (LOWER(q.title) LIKE :pattern ESCAPE '!' OR LOWER(q.body) LIKE :pattern ESCAPE '!')
            """)
    Page<CommunityQuestion> searchVisibleUnanswered(
            @Param("categories") Collection<CommunityCategory> categories,
            @Param("pattern") String pattern,
            Pageable pageable);

    /** Public questions whose author has accepted an answer. */
    @Query(value = """
            SELECT q FROM CommunityQuestion q
            WHERE q.status = com.smartfix.community.domain.CommunityContentStatus.VISIBLE
              AND q.acceptedAnswerId IS NOT NULL
              AND q.category IN :categories
              AND (LOWER(q.title) LIKE :pattern ESCAPE '!' OR LOWER(q.body) LIKE :pattern ESCAPE '!')
            """,
            countQuery = """
            SELECT COUNT(q) FROM CommunityQuestion q
            WHERE q.status = com.smartfix.community.domain.CommunityContentStatus.VISIBLE
              AND q.acceptedAnswerId IS NOT NULL
              AND q.category IN :categories
              AND (LOWER(q.title) LIKE :pattern ESCAPE '!' OR LOWER(q.body) LIKE :pattern ESCAPE '!')
            """)
    Page<CommunityQuestion> searchVisibleSolved(
            @Param("categories") Collection<CommunityCategory> categories,
            @Param("pattern") String pattern,
            Pageable pageable);

    /**
     * Every question one author wrote, newest first, whatever its status.
     *
     * <p>This is the one read that deliberately does not filter on {@code VISIBLE}: "my
     * questions" is where an author goes to see what they withdrew.</p>
     */
    Page<CommunityQuestion> findByAuthorId(Long authorId, Pageable pageable);

    /**
     * One question, but only if this author wrote it.
     *
     * <p>Used by the edit and withdraw flows. Returning empty for someone else's question
     * is what lets the service answer 404 rather than 403, so the response never confirms
     * that another user's question exists.</p>
     */
    Optional<CommunityQuestion> findByIdAndAuthorId(Long id, Long authorId);

    /**
     * How many questions this author has posted since {@code since}.
     *
     * <p>Read by the anti-duplication and rate-limit guard before an insert. Correct only
     * under the caller's own transaction; see the guard for what that does and does not
     * guarantee.</p>
     */
    long countByAuthorIdAndCreatedAtGreaterThanEqual(Long authorId, Instant since);

    /**
     * How many questions this author posted with this exact title and body since
     * {@code since}.
     *
     * <p>Compared case-insensitively, because "Wifi is down" and "wifi is down" are the
     * same question to the person who asked it twice. Both values are already trimmed, by
     * the command DTO on the way in and by the entity before storing, so the comparison
     * is between two normalized strings.</p>
     *
     * <p>This is the narrower of the two guards - identical text inside a short window -
     * and it is what catches a double-submitted form or a double-clicked button.</p>
     */
    long countByAuthorIdAndTitleIgnoreCaseAndBodyIgnoreCaseAndCreatedAtGreaterThanEqual(
            Long authorId, String title, String body, Instant since);

    /**
     * Loads one question and holds a write lock on its row until the transaction ends.
     *
     * <h2>What it is for</h2>
     *
     * <p>Accepting an answer, removing an acceptance and withdrawing an answer all decide
     * their outcome from state that lives in <em>two</em> rows - the answer's status and
     * the question's accepted answer - and a conditional {@code UPDATE} can only make one
     * of them atomic. Without a lock, this interleaving is reachable: one transaction
     * reads the answer as {@code VISIBLE}, a second withdraws that same answer, and the
     * first still commits an acceptance pointing at withdrawn content. The composite
     * foreign key in {@code V18} does not catch it either - it proves the answer belongs
     * to the question, not that it is still visible.</p>
     *
     * <h2>The one lock, and why it is always the same one</h2>
     *
     * <p>Every one of those operations takes <em>this</em> lock, on the question row, and
     * takes it before it touches anything else. Nothing in the module locks an answer
     * row explicitly. A transaction therefore holds at most one such lock, and a cycle
     * between two of them is not expressible - which is what keeps the ordering rule from
     * having to be an ordering rule at all.</p>
     *
     * <p>The answer-withdraw flow reaches the answer first, because its route names an
     * answer and not a question, but it only <em>reads</em> the answer's question id to
     * find out what to lock; it loads the answer itself afterwards, once the lock is
     * held.</p>
     *
     * <p>Reads that only display content deliberately do not come through here. A board
     * listing that took write locks would serialise every reader against every author.</p>
     *
     * @param questionId the question to lock
     * @return the question, or empty when there is none - which callers answer as a 404
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM CommunityQuestion q WHERE q.id = :questionId")
    Optional<CommunityQuestion> findByIdForUpdate(@Param("questionId") Long questionId);

    /**
     * Records an acceptance, but only if the question is still open and the answer is
     * still visible - decided by the database, in one statement.
     *
     * <h2>Why the condition is in the {@code WHERE} clause</h2>
     *
     * <p>The invariant is "at most one accepted answer per question", and it is a
     * transition from {@code NULL} to non-{@code NULL}. A single {@code UPDATE} carrying
     * {@code accepted_answer_id IS NULL} makes that transition atomic: the first caller
     * affects one row and every later caller affects none, with no retry loop and no
     * version to read first. Plan section 12.2 chooses it over optimistic locking for
     * exactly this reason, and the sprint 3 brief forbids replacing it with a check
     * followed by a save.</p>
     *
     * <p>The {@code EXISTS} clause is the second half, and it is the part plan section
     * 12.2 leaves to a separate service-level check. Leaving it there would reopen the
     * race the lock above closes, so it is repeated here instead: the answer has to be
     * <em>this</em> question's answer, and it has to still be {@code VISIBLE}, at the
     * moment the row is written rather than at some earlier moment when it was read.</p>
     *
     * <p>Unlike a JPA entity update, a bulk update does not increment {@code version}.
     * That is safe here only because {@code acceptedAnswerId} is mapped
     * {@code updatable = false}: no entity update statement carries the column, so a
     * writer holding a stale copy cannot put a stale acceptance back. See the field's own
     * comment.</p>
     *
     * @return 1 when this call recorded the acceptance, 0 when the question already had
     *         one, was no longer visible, or the answer was not a visible answer to it
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE CommunityQuestion q
            SET q.acceptedAnswerId = :answerId, q.updatedAt = :now
            WHERE q.id = :questionId
              AND q.acceptedAnswerId IS NULL
              AND q.status = com.smartfix.community.domain.CommunityContentStatus.VISIBLE
              AND EXISTS (SELECT a.id FROM CommunityAnswer a
                          WHERE a.id = :answerId
                            AND a.questionId = :questionId
                            AND a.status = com.smartfix.community.domain.CommunityContentStatus.VISIBLE)
            """)
    int acceptAnswerIfOpen(
            @Param("questionId") Long questionId,
            @Param("answerId") Long answerId,
            @Param("now") Instant now);

    /**
     * Clears a question's accepted answer, leaving the question open.
     *
     * <p>One statement, so the derived solved state cannot end up half-changed. Used by
     * both callers that clear an acceptance: the question's author removing it, and the
     * withdrawal of the answer that held it. Both run it inside the same transaction as
     * the change that motivated it.</p>
     *
     * <p>Returning zero is the ordinary idempotent case, not a failure: removing an
     * acceptance that is already gone, or withdrawing an answer that was not the accepted
     * one, both mean the state that was asked for is the state that holds. That is why
     * this method has no failure branch, and why the conditional on {@code
     * accepted_answer_id} is a guard against clearing a <em>newer</em> acceptance rather
     * than a check for an error.</p>
     *
     * @return 1 when an acceptance was cleared, 0 when there was none to clear
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE CommunityQuestion q
            SET q.acceptedAnswerId = NULL, q.updatedAt = :now
            WHERE q.id = :questionId AND q.acceptedAnswerId IS NOT NULL
            """)
    int clearAcceptanceIfPresent(@Param("questionId") Long questionId, @Param("now") Instant now);
}
