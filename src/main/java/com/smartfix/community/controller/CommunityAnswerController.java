package com.smartfix.community.controller;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.common.exception.BusinessConflictException;
import com.smartfix.common.exception.InputValidationException;
import com.smartfix.community.dto.AnswerFormCommand;
import com.smartfix.community.dto.OwnAnswerResponse;
import com.smartfix.community.service.CommunityAnswerService;
import com.smartfix.community.service.CommunityQueryService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

/**
 * The write routes for answers: post, edit, withdraw, accept and un-accept.
 *
 * <p>These are plan routes A-R8 to A-R13, at the paths the plan gives them. Two of them
 * name both a question and an answer, and both ids are used: the question is the thread
 * being changed and the answer is the one being acted on, and the service refuses the
 * combination outright when the answer belongs to a different question. The pair is not a
 * redundant spelling of one id - it is the pair the ownership rules are stated over, and
 * dropping either would move a decision that belongs in the service into the page.</p>
 *
 * <h2>Authorisation</h2>
 *
 * <p>Every route is for any signed-in account, and none of them is role-specific: an
 * administrator answering, editing or withdrawing here is an ordinary member of the board,
 * and accepting is checked against the question's author whatever the caller's role is.
 * The ownership rules live in {@code CommunityAnswerService}; what this controller decides
 * is which page to render and where to send the browser next.</p>
 *
 * <h2>Route shapes</h2>
 *
 * <p>{@code POST /community/answers/{answerId}} sits beside {@code POST
 * /community/questions/{questionId}}, and the two do not collide: the second path segment
 * is a different literal in each. The edit form is a GET on the same prefix with {@code
 * /edit} appended, so the form and the save are one route apart, exactly as the question's
 * are.</p>
 */
@Controller
public class CommunityAnswerController {

    private static final String ANSWER_FORM_VIEW = "community/answer-form";

    /**
     * The service's message when an acceptance was refused, carried as a flash attribute.
     *
     * <p>A flash attribute and not a query parameter: it is text from the service, it
     * belongs to one redirect, and putting it in the URL would let anybody hand a reader a
     * sentence this application never said.</p>
     */
    private static final String ACCEPTANCE_REFUSED = "acceptanceRefused";

    private final CommunityAnswerService answerService;
    private final CommunityQueryService queryService;
    private final CommunityDetailPageModel detailPage;

    public CommunityAnswerController(
            CommunityAnswerService answerService,
            CommunityQueryService queryService,
            CommunityDetailPageModel detailPage) {
        this.answerService = answerService;
        this.queryService = queryService;
        this.detailPage = detailPage;
    }

    /**
     * Posts an answer and returns to the question, so the reader sees what they wrote.
     *
     * <p>A refusal from the posting guard re-renders the detail page with the answer box
     * still holding the text, rather than a page of its own: the form lives on the thread,
     * and moving it elsewhere to show one message would be a bigger change than the
     * message is worth.</p>
     */
    @PostMapping("/community/questions/{questionId}/answers")
    public String post(
            @PathVariable Long questionId,
            @Valid @ModelAttribute("answerCommand") AnswerFormCommand command,
            BindingResult errors,
            @AuthenticationPrincipal SmartFixUserDetails principal,
            Model model) {
        if (!errors.hasErrors()) {
            try {
                answerService.post(questionId, command, principal.getUserId());
                return "redirect:/community/questions/" + questionId;
            } catch (InputValidationException refused) {
                // The posting guard is not a field rule, so it has nowhere to attach but
                // the form as a whole. Nothing the author typed is lost.
                errors.reject("answer.refused", refused.getMessage());
            }
        }
        // No flag of its own: the refusal is already on the command's binding result as a
        // global error, and the answer box renders that. A second attribute saying the
        // same thing would be a second place for the two to disagree.
        detailPage.populate(model, principal.getUserId(), questionId);
        return CommunityQuestionController.DETAIL_VIEW;
    }

    @GetMapping("/community/answers/{answerId}/edit")
    public String editForm(
            @PathVariable Long answerId,
            @AuthenticationPrincipal SmartFixUserDetails principal,
            Model model) {
        OwnAnswerResponse answer = queryService.findOwnAnswer(principal.getUserId(), answerId);
        AnswerFormCommand command = new AnswerFormCommand();
        command.setBody(answer.body());
        model.addAttribute("command", command);
        model.addAttribute("answer", answer);
        return ANSWER_FORM_VIEW;
    }

    @PostMapping("/community/answers/{answerId}")
    public String saveEdit(
            @PathVariable Long answerId,
            @Valid @ModelAttribute("command") AnswerFormCommand command,
            BindingResult errors,
            @AuthenticationPrincipal SmartFixUserDetails principal,
            Model model) {
        if (!errors.hasErrors()) {
            answerService.edit(answerId, command, principal.getUserId());
            // Read after the save, and only to find the thread to return to. Ownership and
            // visibility were settled by the write above, so this cannot fail for a reason
            // the author cannot see.
            OwnAnswerResponse saved = queryService.findOwnAnswer(principal.getUserId(), answerId);
            return "redirect:/community/questions/" + saved.questionId();
        }
        // A rejected edit keeps the author on the form with their text. The answer is read
        // through the same ownership rule the form itself uses, so a rejected edit to
        // somebody else's answer stays a 404 rather than becoming a 200 with a form on it.
        model.addAttribute("answer", queryService.findOwnAnswer(principal.getUserId(), answerId));
        return ANSWER_FORM_VIEW;
    }

    @PostMapping("/community/answers/{answerId}/withdraw")
    public String withdraw(
            @PathVariable Long answerId,
            @AuthenticationPrincipal SmartFixUserDetails principal) {
        answerService.withdraw(answerId, principal.getUserId());
        // Spelled as the enum constant, because that is what the tab parameter binds to and
        // what the page's own links generate. A literal here is the one place a query string
        // can drift from the links, and it did: "answers" in the lower case matched no
        // constant, so withdrawing an answer sent its author to a 400 page. See
        // CommunityPagesIT.withdrawingTheAcceptedAnswerOpensTheQuestionAgainInTheSameRequest,
        // which now follows this redirect instead of only checking where it pointed.
        return "redirect:/community/mine?tab=ANSWERS";
    }

    /**
     * Accepts an answer to the question in the path.
     *
     * <p>A business conflict - the question already has an accepted answer, either the
     * question or the answer is no longer public, or the author wrote the answer
     * themselves - is shown on the thread rather than as an error page, with the message
     * the service gave. Everything else propagates: a question that is not there, an
     * answer belonging to another question, or an account that is no longer active are all
     * 404s, and rendering them would confirm that the content exists.</p>
     */
    @PostMapping("/community/questions/{questionId}/answers/{answerId}/accept")
    public String accept(
            @PathVariable Long questionId,
            @PathVariable Long answerId,
            @AuthenticationPrincipal SmartFixUserDetails principal,
            RedirectAttributes redirect) {
        try {
            answerService.accept(questionId, answerId, principal.getUserId());
        } catch (BusinessConflictException conflict) {
            // Post/redirect/get, so a refresh cannot re-post the acceptance and the
            // outcome is shown on the thread rather than on an error page.
            redirect.addFlashAttribute(ACCEPTANCE_REFUSED, conflict.getMessage());
        }
        return "redirect:/community/questions/" + questionId;
    }

    /** Removing an acceptance is idempotent, so there is no refusal to show. */
    @PostMapping("/community/questions/{questionId}/acceptance/remove")
    public String removeAcceptance(
            @PathVariable Long questionId,
            @AuthenticationPrincipal SmartFixUserDetails principal) {
        answerService.removeAcceptance(questionId, principal.getUserId());
        return "redirect:/community/questions/" + questionId;
    }
}
