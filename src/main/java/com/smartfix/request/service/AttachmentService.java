package com.smartfix.request.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.smartfix.request.domain.Attachment;
import com.smartfix.request.dto.AttachmentResponse;
import com.smartfix.request.dto.StoredAttachment;
import com.smartfix.request.repository.AttachmentRepository;
import com.smartfix.request.validation.AttachmentValidator;

@Service
public class AttachmentService {

    private final AttachmentValidator validator;
    private final AttachmentStorageService storageService;
    private final AttachmentRepository attachmentRepository;

    public AttachmentService(
            AttachmentValidator validator,
            AttachmentStorageService storageService,
            AttachmentRepository attachmentRepository
    ) {
        this.validator = validator;
        this.storageService = storageService;
        this.attachmentRepository = attachmentRepository;
    }

    /**
     * Validates and stores files on disk.
     *
     * If storing any file fails, files already stored during this
     * operation are deleted before the exception is propagated.
     */
    public List<StoredAttachment> validateAndStore(
            List<MultipartFile> files,
            Long actorUserId
    ) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        validator.validate(files);

        List<StoredAttachment> stored = new ArrayList<>();

        try {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }

                stored.add(storageService.store(file));
            }

            return List.copyOf(stored);

        } catch (RuntimeException exception) {
            deleteStoredFiles(stored);
            throw exception;
        }
    }

    /**
     * Persists metadata for files that have already been stored.
     */
    @Transactional
    public List<AttachmentResponse> saveMetadata(
            Long requestId,
            List<StoredAttachment> stored
    ) {
        if (requestId == null) {
            throw new IllegalArgumentException(
                    "requestId must not be null"
            );
        }

        if (stored == null || stored.isEmpty()) {
            return List.of();
        }

        List<Attachment> attachments =
                stored.stream()
                        .map(file -> Attachment.create(
                                requestId,
                                file.originalFilename(),
                                file.storedFilename(),
                                file.contentType(),
                                file.sizeBytes(),
                                Instant.now()
                        ))
                        .toList();

        return attachmentRepository
                .saveAll(attachments)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> findByRequestId(
            Long requestId
    ) {
        if (requestId == null) {
            throw new IllegalArgumentException(
                    "requestId must not be null"
            );
        }

        return attachmentRepository
                .findAllByRequestIdOrderByIdAsc(requestId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Removes files from private storage.
     *
     * Used as compensation when a later operation fails.
     */
    public void deleteStoredFiles(
            List<StoredAttachment> stored
    ) {
        if (stored == null || stored.isEmpty()) {
            return;
        }

        for (StoredAttachment attachment : stored) {
            try {
                storageService.delete(
                        attachment.storedFilename()
                );
            } catch (RuntimeException ignored) {
                /*
                 * Best-effort compensation.
                 *
                 * Do not hide the original upload/database exception
                 * because cleanup of one file failed.
                 */
            }
        }
    }

    private AttachmentResponse toResponse(
            Attachment attachment
    ) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getCreatedAt()
        );
    }
}