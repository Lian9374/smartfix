package com.smartfix.request.dto;

import java.nio.file.Path;
/**
 * Represents a stored attachment with its metadata.
 * storedFilename / path 是服务器内部使用
 * 绝对不能直接放进浏览器的 AttachmentResponse
 * 浏览器 DTO 不得包含 storedFilename
 */
public record StoredAttachment( 
        String originalFilename,
        String storedFilename,
        String contentType,
        long sizeBytes,
        Path path
) {
}