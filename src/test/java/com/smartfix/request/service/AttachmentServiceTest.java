package com.smartfix.request.service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.smartfix.request.domain.Attachment;
import com.smartfix.request.domain.MaintenanceRequest;
import com.smartfix.request.dto.AttachmentResponse;
import com.smartfix.request.dto.StoredAttachment;
import com.smartfix.request.repository.AttachmentRepository;
import com.smartfix.request.validation.AttachmentValidator;

class AttachmentServiceTest {

    private AttachmentValidator validator;
    private AttachmentStorageService storageService;
    private AttachmentRepository repository;

    private AttachmentService service;

    private RequestAccessService requestAccessService;

    @BeforeEach
    void setUp() {
        validator = mock(AttachmentValidator.class);
        storageService =
                mock(AttachmentStorageService.class);
        repository =
                mock(AttachmentRepository.class);
        requestAccessService =
                mock(RequestAccessService.class);
        service = new AttachmentService(
                validator,
                storageService,
                repository,
                requestAccessService
        );
    }

    @Test
    void validateAndStoreReturnsStoredFiles() {
        MultipartFile file =
                new MockMultipartFile(
                        "files",
                        "photo.png",
                        "image/png",
                        new byte[]{1, 2, 3}
                );

        StoredAttachment stored =
                stored(
                        "photo.png",
                        "uuid.png"
                );

        when(storageService.store(file))
                .thenReturn(stored);

        List<StoredAttachment> result =
                service.validateAndStore(
                        List.of(file),
                        10L
                );

        assertEquals(1, result.size());
        assertEquals(
                "uuid.png",
                result.get(0).storedFilename()
        );

        verify(validator)
                .validate(List.of(file));

        verify(storageService)
                .store(file);
    }

    @Test
    void validateAndStoreDeletesPreviouslyStoredFilesWhenLaterStoreFails() {
        MultipartFile first =
                new MockMultipartFile(
                        "files",
                        "one.png",
                        "image/png",
                        new byte[]{1}
                );

        MultipartFile second =
                new MockMultipartFile(
                        "files",
                        "two.png",
                        "image/png",
                        new byte[]{2}
                );

        MultipartFile third =
                new MockMultipartFile(
                        "files",
                        "three.png",
                        "image/png",
                        new byte[]{3}
                );

        StoredAttachment storedFirst =
                stored(
                        "one.png",
                        "uuid-one.png"
                );

        StoredAttachment storedSecond =
                stored(
                        "two.png",
                        "uuid-two.png"
                );

        when(storageService.store(first))
                .thenReturn(storedFirst);

        when(storageService.store(second))
                .thenReturn(storedSecond);

        when(storageService.store(third))
                .thenThrow(
                        new IllegalStateException(
                                "disk failure"
                        )
                );

        assertThrows(
                IllegalStateException.class,
                () -> service.validateAndStore(
                        List.of(first, second, third),
                        10L
                )
        );

        verify(storageService)
                .delete("uuid-one.png");

        verify(storageService)
                .delete("uuid-two.png");
    }

    @Test
    void validationFailureDoesNotStoreAnything() {
        MultipartFile file =
                new MockMultipartFile(
                        "files",
                        "bad.png",
                        "image/png",
                        new byte[]{1}
                );

        doThrow(
                new IllegalArgumentException(
                        "invalid image"
                )
        ).when(validator)
                .validate(List.of(file));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateAndStore(
                        List.of(file),
                        10L
                )
        );

        verifyNoInteractions(storageService);
    }

    @Test
    void saveMetadataPersistsAttachmentMetadata() {
        StoredAttachment stored =
                stored(
                        "photo.png",
                        "uuid.png"
                );

        when(repository.saveAll(anyList()))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        List<AttachmentResponse> result =
                service.saveMetadata(
                        100L,
                        List.of(stored)
                );

        assertEquals(1, result.size());

        verify(repository)
                .saveAll(anyList());
    }

    @Test
    void findByRequestIdReturnsSafeResponses() {
        Attachment attachment =
                Attachment.create(
                        100L,
                        "photo.png",
                        "secret-uuid.png",
                        "image/png",
                        123L,
                        Instant.now()
                );

        when(
                repository
                        .findAllByRequestIdOrderByIdAsc(
                                100L
                        )
        ).thenReturn(List.of(attachment));

        List<AttachmentResponse> result =
                service.findByRequestId(100L);

        assertEquals(1, result.size());

        AttachmentResponse response =
                result.get(0);

        assertEquals(
                "photo.png",
                response.originalFilename()
        );

        assertEquals(
                "image/png",
                response.contentType()
        );
    }

    @Test
    void cleanupFailureDoesNotHideOriginalStorageFailure() {
        MultipartFile first =
                new MockMultipartFile(
                        "files",
                        "one.png",
                        "image/png",
                        new byte[]{1}
                );

        MultipartFile second =
                new MockMultipartFile(
                        "files",
                        "two.png",
                        "image/png",
                        new byte[]{2}
                );

        StoredAttachment storedFirst =
                stored(
                        "one.png",
                        "uuid-one.png"
                );

        when(storageService.store(first))
                .thenReturn(storedFirst);

        IllegalStateException originalFailure =
                new IllegalStateException(
                        "original disk failure"
                );

        when(storageService.store(second))
                .thenThrow(originalFailure);

        doThrow(
                new IllegalStateException(
                        "cleanup failure"
                )
        ).when(storageService)
                .delete("uuid-one.png");

        IllegalStateException thrown =
                assertThrows(
                        IllegalStateException.class,
                        () -> service.validateAndStore(
                                List.of(first, second),
                                10L
                        )
                );

        assertSame(
                originalFailure,
                thrown
        );
    }

    @Test
    void readAttachmentReturnsNotFoundWhenAttachmentDoesNotBelongToRequest() {
        MaintenanceRequest request =
                mock(MaintenanceRequest.class);

        when(request.getId())
                .thenReturn(100L);

        when(requestAccessService.requireReadableRequest(
                "SF-2026-000001",
                10L
        )).thenReturn(request);

        when(repository.findByIdAndRequestId(
                999L,
                100L
        )).thenReturn(
                java.util.Optional.empty()
        );

        assertThrows(
                com.smartfix.common.exception.ResourceNotFoundException.class,
                () -> service.readAttachment(
                        "SF-2026-000001",
                        999L,
                        10L
                )
        );

        verify(storageService, never())
                .loadAsResource(anyString());
    }

    private StoredAttachment stored(
            String originalFilename,
            String storedFilename
    ) {
        return new StoredAttachment(
                originalFilename,
                storedFilename,
                "image/png",
                123L,
                Path.of(
                        "/tmp/uploads",
                        storedFilename
                )
        );
    }
}