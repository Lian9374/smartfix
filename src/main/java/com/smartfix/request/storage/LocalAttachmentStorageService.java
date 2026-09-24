package com.smartfix.request.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.smartfix.request.config.AttachmentProperties;
import com.smartfix.request.dto.StoredAttachment;
import com.smartfix.request.service.AttachmentStorageService;
import com.smartfix.request.validation.AttachmentValidator;

@Service
public class LocalAttachmentStorageService
    implements AttachmentStorageService {

    private final AttachmentProperties properties;
    private final AttachmentValidator validator;

    public LocalAttachmentStorageService(
            AttachmentProperties properties,
            AttachmentValidator validator
    ) {
        this.properties = properties;
        this.validator = validator;
    }

    @Override
public StoredAttachment store(MultipartFile file) {
        String contentType =
                validator.detectContentType(file);

        String extension = extensionFor(contentType);

        String storedFilename =
                UUID.randomUUID() + extension; // Use UUID to avoid filename collisions

        String originalFilename =
                sanitizeOriginalFilename(
                        file.getOriginalFilename()
                );

        Path uploadDirectory =
                properties.getDir()
                        .toAbsolutePath()
                        .normalize();

        Path target =
                uploadDirectory
                        .resolve(storedFilename)
                        .normalize();

        if (!target.startsWith(uploadDirectory)) {
            throw new IllegalStateException(
                    "Invalid attachment storage path."
            );
        }

        try {
            Files.createDirectories(uploadDirectory);

            try (InputStream input = file.getInputStream()) {
                Files.copy(
                        input,
                        target,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            return new StoredAttachment(
                    originalFilename,
                    storedFilename,
                    contentType,
                    file.getSize(),
                    target
            );

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to store attachment.",
                    e
            );
        }
    }

    @Override
public Resource loadAsResource(String storedFilename) {
    if (storedFilename == null
            || storedFilename.isBlank()) {
        throw new IllegalArgumentException(
                "storedFilename must not be blank."
        );
    }

    Path uploadDirectory =
            properties.getDir()
                    .toAbsolutePath()
                    .normalize();

    Path target =
            uploadDirectory
                    .resolve(storedFilename)
                    .normalize();

    if (!target.startsWith(uploadDirectory)) {
        throw new IllegalArgumentException(
                "Invalid attachment storage key."
        );
    }

    try {
        Resource resource =
                new UrlResource(target.toUri());

        if (!resource.exists()
                || !resource.isReadable()) {
            throw new IllegalStateException(
                    "Stored attachment is unavailable."
            );
        }

        return resource;

    } catch (IOException e) {
        throw new IllegalStateException(
                "Failed to read attachment.",
                e
        );
    }
}

    @Override
    public void delete(String storedFilename) {
        if (storedFilename == null
                || storedFilename.isBlank()) {
            return;
        }

        Path uploadDirectory =
                properties.getDir()
                        .toAbsolutePath()
                        .normalize();

        Path target =
                uploadDirectory
                        .resolve(storedFilename)
                        .normalize();

        if (!target.startsWith(uploadDirectory)) {
            throw new IllegalArgumentException(
                    "Invalid attachment storage key."
            );
        }

        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to delete attachment.",
                    e
            );
        }
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            default -> throw new IllegalArgumentException(
                    "Unsupported attachment type."
            );
        };
    }

    private String sanitizeOriginalFilename(
            String originalFilename
    ) {
        if (originalFilename == null
                || originalFilename.isBlank()) {
            return "attachment";
        }

        String normalized =
                originalFilename.replace('\\', '/');

        int lastSlash =
                normalized.lastIndexOf('/');

        String filename =
                lastSlash >= 0
                        ? normalized.substring(lastSlash + 1)
                        : normalized;

        filename = filename.replaceAll(
                "[\\p{Cntrl}]",
                ""
        );

        if (filename.isBlank()) {
            return "attachment";
        }

        if (filename.length() > 255) {
            filename = filename.substring(0, 255);
        }

        return filename;
    }
}