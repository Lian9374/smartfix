package com.smartfix.community.controller;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.common.exception.BusinessConflictException;
import com.smartfix.common.exception.InputValidationException;
import com.smartfix.community.dto.CommunityReportResponse;
import com.smartfix.community.dto.ResolveReportCommand;
import com.smartfix.community.dto.ReportView;
import com.smartfix.community.service.CommunityModerationService;

import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

/**
 * The moderation routes: A-R17 to A-R22, all under {@code /admin/community/}.
 *
 * <h2>Authorisation, twice, on purpose</h2>
 *
 * <p>Every path here is covered by {@code SecurityConfig}'s
 * {@code .requestMatchers("/admin/**").hasRole("ADMINISTRATOR")}, which is what stops a
 * signed-in requester or technician from reaching any of it. The service repeats the
 * check for every method, because a rule that lives only in a URL pattern is a rule the
 * next caller does not inherit. This controller adds nothing of its own: it decides
 * which page to render and where to send the browser next, and every decision about
 * who may act and what may change is made in {@code CommunityModerationService}.</p>
 *
 * <h2>Six routes and one page</h2>
 *
 * <p>The queue is the only page, and the five writes all redirect back to it. Hiding
 * and restoring are separate routes rather than one route with an action parameter, so
 * that the page's buttons each name one thing and a malformed request cannot ask for
 * an action the page never offered.</p>
 *
 * <h2>Refusals are shown on the page, not thrown at it</h2>
 *
 * <p>A decision that is not a decision (neither upheld nor dismissed), a report
 * somebody else has already settled, and a report that is no longer there are all
 * ordinary things to meet while moderating a shared queue. The first two are flashed
 * back as a message; the third is left to propagate as a 404, because a report id that
 * does not exist is a bad link rather than a moderation outcome.</p>
 */
@Controller
@RequestMapping("/admin/community")
public class AdminCommunityController {

    private static final String VIEW = "admin/community-reports";
    private static final String REDIRECT_TO_QUEUE = "redirect:/admin/community/reports";
    private static final String SUCCESS_MESSAGE = "successMessage";
    private static final String FORM_ERROR = "formError";

    private final CommunityModerationService moderationService;

    public AdminCommunityController(CommunityModerationService moderationService) {
        this.moderationService = moderationService;
    }

    /**
     * Waiting reports are oldest first; handled/all history is newest first and paged.
     *
     * <p>The page size is bounded by the same configuration every other community list
     * uses, so a hand-written {@code size=100000} cannot ask for the whole table.</p>
     */
    @GetMapping("/reports")
    public String queue(
            @RequestParam(defaultValue = "OPEN") ReportView view,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "0") int size,
            @AuthenticationPrincipal SmartFixUserDetails principal,
            Model model) {
        Long actorUserId = principal.getUserId();
        Page<CommunityReportResponse> found =
                moderationService.listReports(actorUserId, view, page, size);

        model.addAttribute("reports", found.getContent());
        model.addAttribute("pagination", found);
        model.addAttribute("openCount", moderationService.countOpenReports(actorUserId));
        model.addAttribute("selectedView", view);
        // So the page can decide whether to offer the resolve form and the governance
        // buttons at all - a courtesy, like every other conditional control in this
        // product: the service refuses an unauthorised call whatever the page renders.
        model.addAttribute("isAdministrator", true);
        return VIEW;
    }

    /**
     * Records a decision on one report, hiding the reported content when asked to.
     *
     * <p>A lost race is reported as such rather than as a success. The service changes
     * nothing when another moderator has already settled the report, and a page that
     * said "the report was resolved" in that case would be describing somebody else's
     * decision as the work of the person reading it.</p>
     */
    @PostMapping("/reports/{reportId}/resolve")
    public String resolve(
            @PathVariable Long reportId,
            @Valid @ModelAttribute("resolveCommand") ResolveReportCommand command,
            BindingResult errors,
            @AuthenticationPrincipal SmartFixUserDetails principal,
            @RequestParam(defaultValue = "OPEN") ReportView view,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "0") int size,
            RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            redirect.addFlashAttribute(FORM_ERROR,
                    "Choose whether the report is upheld or dismissed, then submit it again.");
            return queueRedirect(view, page, size);
        }
        boolean decided;
        try {
            decided = moderationService.resolveReport(reportId, command, principal.getUserId());
        } catch (InputValidationException refused) {
            redirect.addFlashAttribute(FORM_ERROR, refused.getMessage());
            return queueRedirect(view, page, size);
        } catch (BusinessConflictException refused) {
            // Not raised by today's service, which answers a settled report with
            // "nothing changed" rather than an error. Caught anyway so that a future
            // conflict inside the hiding step is shown on the page it came from instead
            // of becoming an error page for a moderator who did nothing wrong.
            redirect.addFlashAttribute(FORM_ERROR, refused.getMessage());
            return queueRedirect(view, page, size);
        }

        if (decided) {
            redirect.addFlashAttribute(SUCCESS_MESSAGE, command.isHideContent()
                    ? "The report was resolved and the content was hidden."
                    : "The report was resolved.");
        } else {
            redirect.addFlashAttribute(FORM_ERROR,
                    "Another moderator had already handled this report, so nothing was changed.");
        }
        return queueRedirect(view, page, size);
    }

    /**
     * Hides a question, and its thread with it.
     *
     * <p>Its answers are not touched - see {@code CommunityQuestion.hide} - so this is
     * one status change and not a cascade.</p>
     */
    @PostMapping("/questions/{questionId}/hide")
    public String hideQuestion(
            @PathVariable Long questionId,
            @AuthenticationPrincipal SmartFixUserDetails principal,
            @RequestParam(defaultValue = "OPEN") ReportView view,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "0") int size,
            RedirectAttributes redirect) {
        boolean changed = moderationService.hideQuestion(questionId, principal.getUserId());
        redirect.addFlashAttribute(changed ? SUCCESS_MESSAGE : FORM_ERROR,
                changed
                        ? "The question is no longer publicly visible."
                        : "The question was already not publicly visible, so nothing changed.");
        return queueRedirect(view, page, size);
    }

    /** Returns a hidden question to public view. Answers and acceptance are untouched. */
    @PostMapping("/questions/{questionId}/restore")
    public String restoreQuestion(
            @PathVariable Long questionId,
            @AuthenticationPrincipal SmartFixUserDetails principal,
            @RequestParam(defaultValue = "OPEN") ReportView view,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "0") int size,
            RedirectAttributes redirect) {
        boolean changed = moderationService.restoreQuestion(questionId, principal.getUserId());
        redirect.addFlashAttribute(changed ? SUCCESS_MESSAGE : FORM_ERROR,
                changed
                        ? "The question is publicly visible again."
                        : "Only a hidden question can be restored; this one is not hidden.");
        return queueRedirect(view, page, size);
    }

    /**
     * Hides an answer.
     *
     * <p>If it is the accepted answer, the question is opened again in the same
     * transaction, which is why the message says so.</p>
     */
    @PostMapping("/answers/{answerId}/hide")
    public String hideAnswer(
            @PathVariable Long answerId,
            @AuthenticationPrincipal SmartFixUserDetails principal,
            @RequestParam(defaultValue = "OPEN") ReportView view,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "0") int size,
            RedirectAttributes redirect) {
        boolean changed = moderationService.hideAnswer(answerId, principal.getUserId());
        redirect.addFlashAttribute(changed ? SUCCESS_MESSAGE : FORM_ERROR,
                changed
                        ? "The answer is no longer publicly visible. If it was the accepted "
                                + "answer, the question is open again."
                        : "The answer was already not publicly visible, so nothing changed.");
        return queueRedirect(view, page, size);
    }

    /**
     * Returns a hidden answer to public view.
     *
     * <p>Not to the accepted state: restoring content is not accepting it, and a
     * question that lost its acceptance when the answer was hidden does not get it
     * back. The message says so, because a moderator seeing the content reappear
     * without the badge would otherwise assume something had gone wrong.</p>
     */
    @PostMapping("/answers/{answerId}/restore")
    public String restoreAnswer(
            @PathVariable Long answerId,
            @AuthenticationPrincipal SmartFixUserDetails principal,
            @RequestParam(defaultValue = "OPEN") ReportView view,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "0") int size,
            RedirectAttributes redirect) {
        boolean changed = moderationService.restoreAnswer(answerId, principal.getUserId());
        redirect.addFlashAttribute(changed ? SUCCESS_MESSAGE : FORM_ERROR,
                changed
                        ? "The answer is publicly visible again. It is not the accepted "
                                + "answer unless the question author accepts it again."
                        : "Only a hidden answer can be restored; this one is not hidden.");
        return queueRedirect(view, page, size);
    }
    private static String queueRedirect(ReportView view, int page, int size) {
        if (view == ReportView.OPEN && page == 0 && size == 0) {
            return REDIRECT_TO_QUEUE;
        }
        return REDIRECT_TO_QUEUE + "?view=" + view.name() + "&page=" + Math.max(0, page)
                + "&size=" + Math.max(0, Math.min(size, 50));
    }
}
