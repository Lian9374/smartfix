package com.smartfix.request.dto;

import com.smartfix.request.spi.RequestDetailContributor.DetailItem;

import java.util.List;
import java.util.Set;

public record RequestPresentationResponse(
        List<AttachmentResponse> attachments,
        List<DetailItem> operationalDetails,
        Set<String> availableActions,
        Integer rating,
        String feedbackComment) {
    // Own the collection structure; preserve existing null/element semantics.
    public RequestPresentationResponse {
        attachments = attachments == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(attachments));
        operationalDetails = operationalDetails == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(operationalDetails));
        availableActions = availableActions == null ? null : java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(availableActions));
    }
}
