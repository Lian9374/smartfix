package com.smartfix.request.dto;

import java.time.Instant;

public record AttachmentResponse(
        Long id,
        String originalFilename,
        String contentType,
        long sizeBytes,
        Instant createdAt
        /* no storeFilename and path
         * because those are internal implementation details that the client should not know about
         */
) {
}
