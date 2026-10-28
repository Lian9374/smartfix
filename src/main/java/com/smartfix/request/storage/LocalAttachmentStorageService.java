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

        Path temporary=uploadDirectory.resolve(storedFilename+".part");
        try {
            Files.createDirectories(uploadDirectory);

            try (InputStream input = file.getInputStream();var output=Files.newOutputStream(temporary,java.nio.file.StandardOpenOption.CREATE_NEW)) {
                byte[] buffer=new byte[8192];long size=0;int read;
                while((read=input.read(buffer))!=-1){size+=read;if(size>properties.getMaxFileSize())throw new IOException("Attachment stream exceeds upload limit");output.write(buffer,0,read);}
            }
            try{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE);}
            catch(java.nio.file.AtomicMoveNotSupportedException unsupported){Files.move(temporary,target);}

            return new StoredAttachment(
                    originalFilename,
                    storedFilename,
                    contentType,
                    Files.size(target),
                    target
            );

        } catch (IOException | RuntimeException e) {
            try{Files.deleteIfExists(temporary);}catch(IOException cleanup){e.addSuppressed(cleanup);}
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

        if (!Files.isRegularFile(target,java.nio.file.LinkOption.NOFOLLOW_LINKS) || !resource.exists()
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
