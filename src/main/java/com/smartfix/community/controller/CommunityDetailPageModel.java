package com.smartfix.community.controller;

import com.smartfix.community.domain.CommunityReportReason;
import com.smartfix.community.dto.CommunityQuestionDetailResponse;
import com.smartfix.community.service.CommunityQueryService;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.util.Arrays;
import java.util.List;

/**
 * The model the question detail page renders, built in one place.
 *
 * <p>Two controllers can land a reader on that page: the question controller when the
 * page is opened, and the answer controller when an answer is posted - including when
 * posting is refused and the answer box has to come back with its text and its message.
 * The page reads three attributes, and one of them decides what the reader is offered, so
 * two copies of this would be two chances for the page to disagree with itself about who
 * the reader is.</p>
 *
 * <p>Everything here comes through the same {@code findQuestionDetail} the page has always
 * used, so the visibility rule is not restated: a question its reader may not see answers
 * a 404 from the service, and this helper cannot turn that into a rendered page.</p>
 */
@Component
class CommunityDetailPageModel {

    private final CommunityQueryService queryService;

    CommunityDetailPageModel(CommunityQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * Fills in the question, whether the reader asked it, and which account the reader is.
     *
     * <p>The reader's own id is put on the model so the page can decide which answers to
     * offer an edit or a withdraw control on. It is the viewer's own id and nothing else -
     * it grants nothing, and every control it enables is refused by the service unless the
     * same test passes there.</p>
     *
     * <p>The report reasons travel with the page for the same reason: the report form is a
     * select of the five reasons the service accepts, and a list built anywhere else could
     * offer a value {@code CommunityReportReason} does not have - which the service would
     * refuse as a bad request rather than as a reason it knows.</p>
     */
    void populate(Model model, Long viewerId, Long questionId) {
        CommunityQuestionDetailResponse question = queryService.findQuestionDetail(viewerId, questionId);
        model.addAttribute("question", question);
        model.addAttribute("isAuthor", question.authorId().equals(viewerId));
        model.addAttribute("viewerId", viewerId);
        model.addAttribute("reportReasons", reportReasons());
    }

    /** The five reasons a report may give, in the order the enumeration declares them. */
    static List<CommunityReportReason> reportReasons() {
        return Arrays.asList(CommunityReportReason.values());
    }
}
