package com.smartfix.request.dto;

import com.smartfix.request.spi.RequestDetailContributor.DetailItem;

import java.util.List;
import java.util.Set;

public record RequestPresentationResponse(
        List<AttachmentResponse> attachments,
        List<DetailItem> operationalDetails,
        Set<String> availableActions,
        Integer rating,
        String feedbackComment) {}
