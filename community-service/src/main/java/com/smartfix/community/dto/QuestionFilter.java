package com.smartfix.community.dto;

/**
 * The three tabs the community list offers.
 *
 * <p>Each constant maps to exactly one predicate, and the mapping lives in
 * {@code CommunityQueryService} rather than here, so the query text stays in one place:</p>
 *
 * <ul>
 *   <li>{@link #LATEST} - every public question, newest first. The default.</li>
 *   <li>{@link #UNANSWERED} - public questions with no accepted answer.</li>
 *   <li>{@link #SOLVED} - public questions with an accepted answer.</li>
 * </ul>
 *
 * <p>"Unanswered" is not "no replies". A question can collect answers and still be open,
 * and {@code accepted_answer_id IS NULL} is the only thing that means unanswered - see
 * the derived-solved-state note on {@code CommunityQuestion}.</p>
 */
public enum QuestionFilter {

    LATEST,
    UNANSWERED,
    SOLVED
}
