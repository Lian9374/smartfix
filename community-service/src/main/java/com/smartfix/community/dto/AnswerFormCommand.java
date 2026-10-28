package com.smartfix.community.dto;

import com.smartfix.community.domain.CommunityAnswer;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The one field a browser may submit when answering a question or editing an answer.
 *
 * <p>One class for both, for the reason {@link QuestionFormCommand} gives: the editable
 * field set is identical, and two classes would be two places to forget the same rule.</p>
 *
 * <h2>What is deliberately absent</h2>
 *
 * <p>{@code questionId}, {@code authorId}, {@code status} and any accepted-answer field.
 * An answer's question and its author come from the URL and the authenticated principal
 * respectively, and a forged field of any of those names is discarded by the binder
 * because there is nothing for it to bind to. Plan section 11.3 requires the command to
 * carry no author, and the accept flow reads its ids from the path rather than from a
 * body - so there is no request field that could name somebody else's answer.</p>
 *
 * <h2>Why the setter trims</h2>
 *
 * <p>{@code @Size} measures what the setter stored, so trimming there is what makes the
 * annotation, the entity's own check and the {@code CHECK} constraint in {@code V17}
 * agree: all three judge the trimmed text. Without it, {@code "  short  "} would clear a
 * ten-character minimum here and then fail the entity's check, turning a form mistake
 * into a 500.</p>
 */
public class AnswerFormCommand {

    // @NotNull then @Size, not @NotBlank beside @Size: on an empty field both would fail
    // and the form would show the same problem twice in two different wordings.
    @NotNull(message = "An answer is required.")
    @Size(min = CommunityAnswer.BODY_MIN_LENGTH, max = CommunityAnswer.BODY_MAX_LENGTH,
            message = "An answer must be between " + CommunityAnswer.BODY_MIN_LENGTH
                    + " and " + CommunityAnswer.BODY_MAX_LENGTH + " characters after trimming.")
    private String body;

    public AnswerFormCommand() {
        // form binding
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body == null ? null : body.trim();
    }
}
