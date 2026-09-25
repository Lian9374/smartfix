package com.smartfix.request.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import com.smartfix.request.dto.StoredAttachment;
/**
 * Service interface for storing and deleting attachments.
 */
public interface AttachmentStorageService {

    StoredAttachment store(MultipartFile file);

    Resource loadAsResource(String storedFilename);
    
    void delete(String storedFilename);
}