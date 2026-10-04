package com.smartfix.request.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.request.dto.ReadableAttachment;
import com.smartfix.request.service.AttachmentService;

class AttachmentControllerTest {

    private AttachmentService attachmentService;
    private AttachmentController controller;
    private SmartFixUserDetails principal;

    @BeforeEach
    void setUp() {
        attachmentService =
                mock(AttachmentService.class);

        controller =
                new AttachmentController(
                        attachmentService
                );

        principal =
                mock(SmartFixUserDetails.class);

        when(principal.getUserId())
                .thenReturn(10L);
    }

    @Test
    void downloadAttachmentReturnsSecureResponse() {
        ByteArrayResource resource =
                new ByteArrayResource(
                        new byte[]{1, 2, 3}
                );

        ReadableAttachment attachment =
                new ReadableAttachment(
                        5L,
                        "evidence.png",
                        "image/png",
                        3L,
                        resource
                );

        when(
                attachmentService.readAttachment(
                        "SF-2026-000001",
                        5L,
                        10L
                )
        ).thenReturn(attachment);

        ResponseEntity<?> response =
                controller.downloadAttachment(
                        "SF-2026-000001",
                        5L,
                        principal
                );

        assertEquals(
                200,
                response.getStatusCode().value()
        );

        assertEquals(
                "image/png",
                response.getHeaders()
                        .getContentType()
                        .toString()
        );

        assertEquals(
                3L,
                response.getHeaders()
                        .getContentLength()
        );

        assertEquals(
                "nosniff",
                response.getHeaders()
                        .getFirst(
                                "X-Content-Type-Options"
                        )
        );

        assertEquals(
                "private, no-store",
                response.getHeaders()
                        .getCacheControl()
        );

        String disposition =
                response.getHeaders()
                        .getFirst(
                                "Content-Disposition"
                        );

        assertNotNull(disposition);

        assertTrue(
                disposition.startsWith(
                        "attachment"
                )
        );

        assertSame(
                resource,
                response.getBody()
        );

        verify(attachmentService)
                .readAttachment(
                        "SF-2026-000001",
                        5L,
                        10L
                );
    }
}