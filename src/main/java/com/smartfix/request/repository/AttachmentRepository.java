package com.smartfix.request.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartfix.request.domain.Attachment;

public interface AttachmentRepository
        extends JpaRepository<Attachment, Long> {

    @org.springframework.data.jpa.repository.Query("select a.storedFilename from Attachment a")
    List<String> storedKeys();

    List<Attachment> findAllByRequestIdOrderByIdAsc(
            Long requestId
    );

    Optional<Attachment> findByIdAndRequestId( // must verify parent-child relationship before deletion
            Long id,
            Long requestId
    );
}
