package com.smartfix.community.controller;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.common.exception.BusinessConflictException;
import com.smartfix.community.dto.ReportContentCommand;
import com.smartfix.community.service.CommunityModerationService;
import com.smartfix.community.service.CommunityQueryService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

/**
 * The two report routes: A-R14 for a question and A-R15 for an answer.
 *
 * <h2>Why a report is a post/redirect/get and not a re-rendered form</h2>
 *
 * <p>The report form lives on the question thread, inside a disclosure beside the
 * content it is about, and there is one of them per answer as well as one for the
 * question. Re-rendering that page from this controller on a refusal would mean
 * carrying every one of those forms' state back with it; instead both outcomes are
 * flashed onto the redirect target, exactly as the accept route next door shows a
 * refused acceptance. A flash attribute and never a query parameter: these are
 * sentences this application wrote, and a URL carrying them would let anybody hand a
 * reader words the service never said.</p>
 *
 * <p>The answer route redirects to the answer's <em>question</em>, and the question it
 * redirects to is read back from the service rather than taken from the form. A
 * redirect target that arrived in the request body would be a second, unvalidated
 * spelling of something the service already decided - and the read applies the same
 * visibility rule the report itself had to satisfy, so the two cannot disagree.</p>
 *
 * <h2>Authorisation</h2>
 *
 * <p>Any signed-in account may report; {@code /community/**} is
 * {@code .authenticated()} in {@code SecurityConfig}. Whether the account is still
 * ACTIVE, whether the target is public, and whether this account has already reported
 * it are decided by {@code CommunityModerationService}, which is the only place those
 * rules live.</p>
 */
@Controller
public class CommunityReportController {

    /** Flashed when a report was filed. */
    static final String REPORT_RECEIVED = "reportReceived";

    /** Flashed when a report was refused, carrying the service's own sentence. */
    static final String REPORT_REFUSED = "reportRefused";

    /**
     * Shown when the submission is missing a reason altogether.
     *
     * <p>A constant rather than the binding error's own text: the browser refuses to
     * submit the form without a reason (the select is {@code required}), so reaching
     * this branch means the request was built by hand, and the message that matters then
     * is the one the service would have given.</p>
     */
    private static final String REASON_REQUIRED =
            "Choose a reason for the report, then submit it again.";

    private final CommunityModerationService moderationService;
    private final CommunityQueryService queryService;

    public CommunityReportController(
            CommunityModerationService moderationService,
            CommunityQueryService queryService) {
        this.moderationService = moderationService;
        this.queryService = queryService;
    }

    /**
     * Reports the question in the path.
     *
     * <p>The question id is used for the redirect only after the service has accepted
     * the report against it, so the page the reporter lands on is the page they
     * reported from.</p>
     */
    @PostMapping("/community/questions/{questionId}/reports")
    public String reportQuestion(
            @PathVariable Long questionId,
            @Valid @ModelAttribute("reportCommand") ReportContentCommand command,
            BindingResult errors,
            @AuthenticationPrincipal SmartFixUserDetails principal,
            RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            redirect.addFlashAttribute(REPORT_REFUSED, REASON_REQUIRED);
        } else {
            try {
                moderationService.reportQuestion(questionId, command, principal.getUserId());
                redirect.addFlashAttribute(REPORT_RECEIVED,
                        "Thank you. A moderator will review this question.");
            } catch (BusinessConflictException refused) {
                redirect.addFlashAttribute(REPORT_REFUSED, refused.getMessage());
            }
        }
        return "redirect:/community/questions/" + questionId;
    }

    /** Reports the answer in the path, and returns to the thread it was read on. */
    @PostMapping("/community/answers/{answerId}/reports")
    public String reportAnswer(
            @PathVariable Long answerId,
            @Valid @ModelAttribute("reportCommand") ReportContentCommand command,
            BindingResult errors,
            @AuthenticationPrincipal SmartFixUserDetails principal,
            RedirectAttributes redirect) {
        Long actorUserId = principal.getUserId();
        if (errors.hasErrors()) {
            redirect.addFlashAttribute(REPORT_REFUSED, REASON_REQUIRED);
        } else {
            try {
                moderationService.reportAnswer(answerId, command, actorUserId);
                redirect.addFlashAttribute(REPORT_RECEIVED,
                        "Thank you. A moderator will review this answer.");
            } catch (BusinessConflictException refused) {
                redirect.addFlashAttribute(REPORT_REFUSED, refused.getMessage());
            }
        }
        // Read after the write, and through the same visibility rule the report had to
        // satisfy: a refusal on an answer nobody may read stays a 404 rather than
        // becoming a redirect to a page that does not render.
        return "redirect:/community/questions/" + queryService.findThreadIdOfAnswer(actorUserId, answerId);
    }
}
