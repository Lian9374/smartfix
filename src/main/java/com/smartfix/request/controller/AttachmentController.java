package com.smartfix.request.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.request.dto.ReadableAttachment;
import com.smartfix.request.service.AttachmentService;

@RestController
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(
            AttachmentService attachmentService
    ) {
        this.attachmentService = attachmentService;
    }

    @GetMapping(
            "/requests/{ticketNumber}/attachments/{attachmentId}"
    )
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable String ticketNumber,
            @PathVariable Long attachmentId,
            @AuthenticationPrincipal SmartFixUserDetails principal
    ) {
        ReadableAttachment attachment =
                attachmentService.readAttachment(
                        ticketNumber,
                        attachmentId,
                        principal.getUserId()
                );

        ContentDisposition disposition =
                ContentDisposition.attachment()
                        .filename(
                                attachment.originalFilename(),
                                StandardCharsets.UTF_8
                        )
                        .build();

        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(
                                attachment.contentType()
                        )
                )
                .contentLength(
                        attachment.sizeBytes()
                )
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposition.toString()
                )
                .header(
                        "X-Content-Type-Options",
                        "nosniff"
                )
                .header(
                        HttpHeaders.CACHE_CONTROL,
                        "private, no-store"
                )
                .body(
                        attachment.resource()
                );
    }
}
