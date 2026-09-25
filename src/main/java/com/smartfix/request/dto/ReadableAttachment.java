package com.smartfix.request.dto;

import org.springframework.core.io.Resource;

public record ReadableAttachment(
        Long id,
        String originalFilename,
        String contentType,
        long sizeBytes,
        Resource resource
) {
}