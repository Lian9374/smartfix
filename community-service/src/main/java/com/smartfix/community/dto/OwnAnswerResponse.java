package com.smartfix.community.dto;

/**
 * One of the caller's own answers, as the edit form needs it.
 *
 * <p>The smallest thing that lets the form render: the text to prefill, and the question
 * to return to. The question's title is not here - the edit form does not have to name
 * the question, and the fewer places that decide whether a question may be named, the
 * fewer places can disagree about it.</p>
 *
 * @param answerId   the answer being edited
 * @param questionId the question it answers, so the form can link back to the thread
 * @param body       the current text
 */
public record OwnAnswerResponse(
        Long answerId,
        Long questionId,
        String body
) {
}
