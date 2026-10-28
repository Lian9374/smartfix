package com.smartfix.community.dto;

import com.smartfix.community.domain.CommunityCategory;
import com.smartfix.community.domain.CommunityQuestion;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The fields a browser may submit when asking a question or editing one.
 *
 * <p>One class for both forms because the editable field set is identical, and because
 * keeping them separate would be two places to forget the same rule.</p>
 *
 * <h2>What is deliberately absent</h2>
 *
 * <p>{@code authorId}, {@code status} and {@code acceptedAnswerId} are not here, so a
 * forged field of any of those names has nothing to bind to and is discarded by the
 * binder rather than trusted. Plan section 11.3 ends with the same instruction, and
 * section 5.3 makes author identity an invariant of the authenticated principal: the
 * service takes the author from {@code SmartFixUserDetails.getUserId()}, never from a
 * request parameter.</p>
 *
 * <h2>Why the setters trim</h2>
 *
 * <p>{@code @Size} measures the value the setter stored, so trimming there is what makes
 * the annotation agree with the entity and with the database {@code CHECK}: all three
 * judge the trimmed text. Without it, {@code "  a  "} would clear a three-character
 * minimum here and then fail the entity's check, turning a form mistake into a 500.</p>
 */
public class QuestionFormCommand {

    // @NotNull then @Size, rather than @NotBlank beside @Size: on an empty field @NotBlank
    // and a minimum-length @Size both fail, and the form would show the same problem
    // twice in two different wordings. These two cannot both fire.
    @NotNull(message = "Title is required.")
    @Size(min = CommunityQuestion.TITLE_MIN_LENGTH, max = CommunityQuestion.TITLE_MAX_LENGTH,
            message = "Title must be between " + CommunityQuestion.TITLE_MIN_LENGTH
                    + " and " + CommunityQuestion.TITLE_MAX_LENGTH + " characters after trimming.")
    private String title;

    @NotNull(message = "Details are required.")
    @Size(min = CommunityQuestion.BODY_MIN_LENGTH, max = CommunityQuestion.BODY_MAX_LENGTH,
            message = "Details must be between " + CommunityQuestion.BODY_MIN_LENGTH
                    + " and " + CommunityQuestion.BODY_MAX_LENGTH + " characters after trimming.")
    private String body;

    @NotNull(message = "Topic is required.")
    private CommunityCategory category;

    public QuestionFormCommand() {
        // form binding
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = trim(title);
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = trim(body);
    }

    public CommunityCategory getCategory() {
        return category;
    }

    public void setCategory(CommunityCategory category) {
        this.category = category;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
