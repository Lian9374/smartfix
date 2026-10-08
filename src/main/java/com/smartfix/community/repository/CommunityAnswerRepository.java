package com.smartfix.community.repository;

import com.smartfix.community.domain.CommunityAnswer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persistence owned by the community module for answers.
 *
 * <p>Unlike the question repository, nothing here takes a row lock. Accepting, removing
 * an acceptance and withdrawing an answer all serialise on the <em>question</em> row -
 * see {@code CommunityQuestionRepository.findByIdForUpdate} - and an answer is only ever
 * written underneath that lock, so a second lock here would add a second thing to order
 * without adding a guarantee.</p>
 */
public interface CommunityAnswerRepository extends JpaRepository<CommunityAnswer, Long> {

    /**
     * Every answer to one question, oldest first.
     *
     * <p>Deliberately unfiltered: the caller decides what a reader may see, because a
     * withdrawn answer must still be shown to its own author and to an administrator as a
     * placeholder rather than vanishing from the thread. A hard {@code status = 'VISIBLE'}
     * filter here would make that impossible to render.</p>
     */
    List<CommunityAnswer> findByQuestionIdOrderByCreatedAtAscIdAsc(Long questionId);

    /** One answer, but only if this author wrote it. Empty means "not yours or not there". */
    Optional<CommunityAnswer> findByIdAndAuthorId(Long id, Long authorId);

    long countByQuestionId(Long questionId);

    /**
     * The question an answer belongs to, but only if this author wrote the answer.
     *
     * <h2>Why this returns an id and not the answer</h2>
     *
     * <p>Answer withdrawal starts from an answer id, so it has to discover which question
     * to lock - and that read must not put the answer into the persistence context. If it
     * did, the later load under the lock would be served from the first-level cache, and
     * the whole point of taking the lock is to read the answer <em>after</em> it. A
     * scalar projection loads no entity, so nothing is cached and the post-lock read
     * really goes to the database.</p>
     *
     * <p>Filtering by author here is also the ownership check: a stranger gets an empty
     * result and therefore a 404, without a row lock ever being taken on somebody else's
     * question.</p>
     */
    @Query("SELECT a.questionId FROM CommunityAnswer a WHERE a.id = :answerId AND a.authorId = :authorId")
    Optional<Long> findQuestionIdOfOwnAnswer(
            @Param("answerId") Long answerId, @Param("authorId") Long authorId);

    /**
     * The question an answer belongs to, whoever wrote either of them.
     *
     * <p>The moderation variant of the query above, and it exists for the same reason
     * rather than for convenience: hiding or restoring an answer starts from an answer
     * id, has to discover which question row to lock, and must not put the answer into
     * the persistence context before that lock is taken. A scalar projection loads no
     * entity, so the answer is read <em>after</em> the question row is locked and the
     * moderator's decision is made against the state the lock protects.</p>
     *
     * <p>There is deliberately no author filter. Moderation is not the author's flow:
     * the caller's permission is the administrator role, checked by the service before
     * this query runs, and an answer is hidden by whoever the moderation screen says is
     * hiding it.</p>
     */
    @Query("SELECT a.questionId FROM CommunityAnswer a WHERE a.id = :answerId")
    Optional<Long> findQuestionIdById(@Param("answerId") Long answerId);

    /**
     * Every answer one author wrote, newest first, whatever its status.
     *
     * <p>Answers carry no {@code VISIBLE} filter here, for the same reason "my questions"
     * has none: a withdrawn answer is exactly what its author comes to this list to
     * confirm. The parent questions are deliberately not joined in - whether an entry may
     * name its question is a second decision, made in the query service against the same
     * visibility rule the detail page uses.</p>
     */
    Page<CommunityAnswer> findByAuthorId(Long authorId, Pageable pageable);

    /**
     * How many answers this author posted since {@code since}.
     *
     * <p>The answer half of the posting guard. As with the question guard it is a count
     * followed by an insert and the two are not atomic with respect to each other; see
     * {@code CommunityAnswerService} for what that does and does not bound.</p>
     */
    long countByAuthorIdAndCreatedAtGreaterThanEqual(Long authorId, Instant since);
}
