package com.smartfix.request.service;

import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.request.repository.AttachmentRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validates a repair evidence reference without exposing storage details to workorder. */
@Service
@Transactional(readOnly = true)
public class RequestEvidenceService {
    private final AttachmentRepository attachments;

    public RequestEvidenceService(AttachmentRepository attachments) {
        this.attachments = attachments;
    }

    public void requireBelongsToRequest(Long attachmentId, Long requestId) {
        attachments
                .findByIdAndRequestId(attachmentId, requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
    }
}
