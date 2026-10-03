package com.smartfix.request.controller;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.request.service.AttachmentService;

import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@Controller
public class AttachmentController {
    private final AttachmentService attachments;

    public AttachmentController(AttachmentService attachments) {
        this.attachments = attachments;
    }

    @GetMapping("/requests/{ticket}/attachments/{id}")
    public ResponseEntity<Resource> download(
            @PathVariable String ticket,
            @PathVariable Long id,
            @AuthenticationPrincipal SmartFixUserDetails p) {
        var a = attachments.readAttachment(ticket, id, p.getUserId());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.contentType()))
                .contentLength(a.sizeBytes())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(a.originalFilename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(a.resource());
    }
}
