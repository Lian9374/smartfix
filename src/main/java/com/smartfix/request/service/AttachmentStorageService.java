package com.smartfix.request.service;

import com.smartfix.request.dto.StoredAttachment;
import org.springframework.web.multipart.MultipartFile;
/**
 * Service interface for storing and deleting attachments.
 */
public interface AttachmentStorageService {

    StoredAttachment store(MultipartFile file);

    void delete(String storedFilename);
}