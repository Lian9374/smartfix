package com.smartfix.request.validation;

import com.smartfix.request.config.AttachmentProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Component
public class AttachmentValidator {

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47,
            0x0D, 0x0A, 0x1A, 0x0A
    };

    private final AttachmentProperties properties;

    public AttachmentValidator(AttachmentProperties properties) {
        this.properties = properties;
    }

    public void validate(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return;
        }

        List<MultipartFile> nonEmptyFiles = files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();

        if (nonEmptyFiles.size() > properties.getMaxFiles()) {
            throw new IllegalArgumentException(
                    "A maximum of " + properties.getMaxFiles()
                            + " attachments is allowed."
            );
        }

        long totalSize = 0L;

        for (MultipartFile file : nonEmptyFiles) {
            totalSize += file.getSize();

            if (file.getSize() > properties.getMaxFileSize()) {
                throw new IllegalArgumentException(
                        "An attachment exceeds the maximum allowed file size."
                );
            }

            if (totalSize > properties.getMaxTotalSize()) {
                throw new IllegalArgumentException(
                        "The total attachment size exceeds the allowed limit."
                );
            }

            validateImage(file);
        }
    }

    public String detectContentType(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(8);

            if (isPng(header)) {
                return "image/png";
            }

            if (isJpeg(header)) {
                return "image/jpeg";
            }

            throw new IllegalArgumentException(
                    "Only PNG and JPEG images are allowed."
            );
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Unable to inspect attachment.",
                    e
            );
        }
    }

    private void validateImage(MultipartFile file) {
        if (file.getSize() <= 0) {
            throw new IllegalArgumentException(
                    "Attachment must not be empty."
            );
        }

        detectContentType(file);

        BufferedImage image;

        try (InputStream input = file.getInputStream()) {
            image = ImageIO.read(input);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Unable to decode attachment.",
                    e
            );
        }

        if (image == null) {
            throw new IllegalArgumentException(
                    "Attachment is not a valid image."
            );
        }

        int width = image.getWidth();
        int height = image.getHeight();

        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "Image dimensions are invalid."
            );
        }

        if (width > properties.getMaxDimension()
                || height > properties.getMaxDimension()) {
            throw new IllegalArgumentException(
                    "Image dimensions exceed the allowed limit."
            );
        }

        long pixels = (long) width * height;

        if (pixels > properties.getMaxPixels()) {
            throw new IllegalArgumentException(
                    "Image pixel count exceeds the allowed limit."
            );
        }
    }

    private boolean isPng(byte[] header) {
        if (header.length < PNG_SIGNATURE.length) {
            return false;
        }

        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (header[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }

        return true;
    }

    private boolean isJpeg(byte[] header) {
        return header.length >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF;
    }
}