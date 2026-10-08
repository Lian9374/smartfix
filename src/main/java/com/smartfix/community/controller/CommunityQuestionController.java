package com.smartfix.community.controller;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.common.exception.InputValidationException;
import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.community.domain.CommunityCategory;
import com.smartfix.community.dto.AnswerFormCommand;
import com.smartfix.community.dto.CommunityQuestionDetailResponse;
import com.smartfix.community.dto.CommunityQuestionSummaryResponse;
import com.smartfix.community.dto.MineTab;
import com.smartfix.community.dto.MyAnswerResponse;
import com.smartfix.community.dto.QuestionFilter;
import com.smartfix.community.dto.QuestionFormCommand;
import com.smartfix.community.service.CommunityQueryService;
import com.smartfix.community.service.CommunityQuestionService;

import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.validation.Valid;

import java.util.Arrays;
import java.util.List;

/**
 * Pages for browsing and maintaining community questions.
 *
 * <p>Answers are posted through {@link CommunityAnswerController}, which owns the answer
 * routes and the form that posts to them. This controller renders the thread they appear
 * on and offers no control for a route nobody serves: a button that posts to a missing
 * endpoint looks like a broken feature rather than an absent one. Moderation and reporting
 * are still later phases.</p>
 *
 * <h2>Authorisation</h2>
 *
 * <p>This controller decides nothing about who may do what. Every route is for any
 * signed-in account, and the ownership rules live in the services, which is the only
 * place they can be enforced for callers that never pass through HTTP. What the pages do
 * is avoid offering an action the service would refuse; that is a courtesy, not a
 * control.</p>
 *
 * <h2>Route ordering</h2>
 *
 * <p>{@code /community/questions/new} and {@code /community/questions/{questionId}} both
 * match a GET of {@code /community/questions/new}. Spring Boot 3 matches with {@code
 * PathPatternParser}, which prefers the literal segment, so the form wins - and because
 * {@code questionId} is a {@code Long}, the alternative would be a conversion error, not
 * a silent wrong page. There is a test that posts and reads through this specific path so
 * the precedence is verified rather than assumed.</p>
 */
@Controller
public class CommunityQuestionController {

    private static final String BROWSE_VIEW = "community/index";
    private static final String FORM_VIEW = "community/question-form";
    private static final String MY_QUESTIONS_VIEW = "community/mine";

    /**
     * The question thread. Package-visible because the answer controller renders it too -
     * when an answer is refused, the box that refused it lives on this page.
     */
    static final String DETAIL_VIEW = "community/question";

    private final CommunityQueryService queryService;
    private final CommunityQuestionService questionService;
    private final CommunityDetailPageModel detailPage;

    public CommunityQuestionController(
            CommunityQueryService queryService,
            CommunityQuestionService questionService,
            CommunityDetailPageModel detailPage) {
        this.queryService = queryService;
        this.questionService = questionService;
        this.detailPage = detailPage;
    }

    /** Every page that renders the question form needs the same topic list. */
    @ModelAttribute("categories")
    public List<CommunityCategory> categories() {
        return Arrays.asList(CommunityCategory.values());
    }

    @GetMapping("/community")
    public String browse(
            @AuthenticationPrincipal SmartFixUserDetails principal,
            @RequestParam(required = false) CommunityCategory category,
            @RequestParam(required = false) QuestionFilter filter,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        Page<CommunityQuestionSummaryResponse> result = queryService.browse(
                principal.getUserId(), category, filter, q, page, size);
        model.addAttribute("questions", result.getContent());
        model.addAttribute("pagination", result);
        model.addAttribute("filters", QuestionFilter.values());
        model.addAttribute("selectedCategory", category);
        model.addAttribute("selectedFilter", filter == null ? QuestionFilter.LATEST : filter);
        model.addAttribute("query", q == null ? "" : q.trim());
        model.addAttribute("page", result.getNumber());
        // Told apart on purpose: an empty board and a filter that matched nothing call
        // for different words, and a page that says "no questions yet" to someone who
        // just searched for something is simply wrong.
        model.addAttribute("unfiltered", category == null && (q == null || q.isBlank()));
        return BROWSE_VIEW;
    }

    /**
     * The caller's own content, under two tabs.
     *
     * <p>The tab is a query parameter with a default rather than a separate route, so the
     * two lists share the paging model and a link to either one is a plain URL. An
     * unrecognised value is a 400 from the converter, not a silent fallback to the first
     * tab: quietly showing questions to someone who asked for answers is its own small
     * lie.</p>
     */
    @GetMapping("/community/mine")
    public String mine(
            @AuthenticationPrincipal SmartFixUserDetails principal,
            @RequestParam(required = false) MineTab tab,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        MineTab selected = tab == null ? MineTab.QUESTIONS : tab;
        model.addAttribute("tab", selected);

        // Only the selected tab's list is loaded. The other tab's count is not shown, so
        // running its query would be work with nowhere to put the result.
        if (selected == MineTab.ANSWERS) {
            Page<MyAnswerResponse> result = queryService.listMyAnswers(principal.getUserId(), page, size);
            model.addAttribute("answers", result.getContent());
            model.addAttribute("pagination", result);
            model.addAttribute("page", result.getNumber());
        } else {
            Page<CommunityQuestionSummaryResponse> result =
                    queryService.listMyQuestions(principal.getUserId(), page, size);
            model.addAttribute("questions", result.getContent());
            model.addAttribute("pagination", result);
            model.addAttribute("page", result.getNumber());
        }
        return MY_QUESTIONS_VIEW;
    }

    @GetMapping("/community/questions/new")
    public String newQuestionForm(Model model) {
        model.addAttribute("command", new QuestionFormCommand());
        model.addAttribute("editing", false);
        return FORM_VIEW;
    }

    @PostMapping("/community/questions")
    public String ask(
            @Valid @ModelAttribute("command") QuestionFormCommand command,
            BindingResult errors,
            @AuthenticationPrincipal SmartFixUserDetails principal,
            Model model) {
        if (!errors.hasErrors()) {
            try {
                Long questionId = questionService.ask(command, principal.getUserId());
                return "redirect:/community/questions/" + questionId;
            } catch (InputValidationException refused) {
                // The posting guards are not field rules, so they have nowhere to attach
                // but the form as a whole. The command is re-rendered unchanged, so
                // nothing the author typed is lost.
                errors.reject("question.refused", refused.getMessage());
            }
        }
        model.addAttribute("editing", false);
        return FORM_VIEW;
    }

    @GetMapping("/community/questions/{questionId}")
    public String question(
            @PathVariable Long questionId,
            @AuthenticationPrincipal SmartFixUserDetails principal,
            Model model) {
        // The same helper the answer controller uses, so opening the thread and being
        // returned to it after posting an answer cannot disagree about what the reader
        // may do here.
        detailPage.populate(model, principal.getUserId(), questionId);
        model.addAttribute("answerCommand", new AnswerFormCommand());
        return DETAIL_VIEW;
    }

    @GetMapping("/community/questions/{questionId}/edit")
    public String editForm(
            @PathVariable Long questionId,
            @AuthenticationPrincipal SmartFixUserDetails principal,
            Model model) {
        // Reading the question through the same visibility rule the detail page uses is
        // what makes someone else's question a 404 here, before any form is rendered.
        CommunityQuestionDetailResponse question =
                queryService.findQuestionDetail(principal.getUserId(), questionId);
        if (!question.authorId().equals(principal.getUserId())) {
            throw new ResourceNotFoundException("Community content not found.");
        }
        QuestionFormCommand command = new QuestionFormCommand();
        command.setTitle(question.title());
        command.setBody(question.body());
        command.setCategory(question.category());
        model.addAttribute("command", command);
        model.addAttribute("question", question);
        model.addAttribute("editing", true);
        return FORM_VIEW;
    }

    @PostMapping("/community/questions/{questionId}")
    public String saveEdit(
            @PathVariable Long questionId,
            @Valid @ModelAttribute("command") QuestionFormCommand command,
            BindingResult errors,
            @AuthenticationPrincipal SmartFixUserDetails principal,
            Model model) {
        if (!errors.hasErrors()) {
            questionService.edit(questionId, command, principal.getUserId());
            return "redirect:/community/questions/" + questionId;
        }
        // A rejected edit re-renders the form the author was on, with their text intact.
        // The question is re-read so the page can still title itself; it goes through the
        // same ownership check, so a rejected edit to someone else's question stays a 404
        // instead of becoming a 200 with a form on it.
        CommunityQuestionDetailResponse question =
                queryService.findQuestionDetail(principal.getUserId(), questionId);
        if (!question.authorId().equals(principal.getUserId())) {
            throw new ResourceNotFoundException("Community content not found.");
        }
        model.addAttribute("question", question);
        model.addAttribute("editing", true);
        return FORM_VIEW;
    }

    @PostMapping("/community/questions/{questionId}/withdraw")
    public String withdraw(
            @PathVariable Long questionId,
            @AuthenticationPrincipal SmartFixUserDetails principal) {
        questionService.withdraw(questionId, principal.getUserId());
        return "redirect:/community/mine";
    }
}
