package com.smartfix.request.dto;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

public record UploadAttachmentCommand(
        List<MultipartFile> files // just files, no requestId, because the requestId is already in the URL path and will be passed to the service layer separately
) {

    public UploadAttachmentCommand {
        files = files == null
                ? List.of()
                : List.copyOf(files);
    }
}