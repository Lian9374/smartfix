package com.smartfix.request.storage;

import com.smartfix.request.config.AttachmentProperties;
import com.smartfix.request.dto.StoredAttachment;
import com.smartfix.request.validation.AttachmentValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalAttachmentStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalAttachmentStorageService storageService;

    @BeforeEach
    void setUp() {
        AttachmentProperties properties =
                new AttachmentProperties();

        properties.setDir(tempDir);

        AttachmentValidator validator =
                new AttachmentValidator(properties);

        storageService =
                new LocalAttachmentStorageService(
                        properties,
                        validator
                );
    }

    @Test
    void storesUsingUuidFilename() throws IOException {
        MockMultipartFile file =
                new MockMultipartFile(
                        "files",
                        "cat.png",
                        "image/png",
                        createPng()
                );

        StoredAttachment stored =
                storageService.store(file);

        assertEquals(
                "cat.png",
                stored.originalFilename()
        );

        assertNotEquals(
                "cat.png",
                stored.storedFilename()
        );

        assertTrue(
                stored.storedFilename().endsWith(".png")
        );

        assertTrue(
                Files.exists(stored.path())
        );
    }

    @Test
    void pathTraversalFilenameDoesNotEscapeUploadDirectory()
            throws IOException {

        MockMultipartFile file =
                new MockMultipartFile(
                        "files",
                        "../../etc/passwd.png",
                        "image/png",
                        createPng()
                );

        StoredAttachment stored =
                storageService.store(file);

        Path normalizedTemp =
                tempDir.toAbsolutePath().normalize();

        Path normalizedStored =
                stored.path()
                        .toAbsolutePath()
                        .normalize();

        assertTrue(
                normalizedStored.startsWith(normalizedTemp)
        );

        assertEquals(
                "passwd.png",
                stored.originalFilename()
        );

        assertFalse(
                stored.storedFilename().contains("..")
        );
    }

    @Test
    void deletesStoredFile() throws IOException {
        MockMultipartFile file =
                new MockMultipartFile(
                        "files",
                        "cat.png",
                        "image/png",
                        createPng()
                );

        StoredAttachment stored =
                storageService.store(file);

        assertTrue(
                Files.exists(stored.path())
        );

        storageService.delete(
                stored.storedFilename()
        );

        assertFalse(
                Files.exists(stored.path())
        );
    }

    private byte[] createPng()
            throws IOException {

        BufferedImage image =
                new BufferedImage(
                        20,
                        20,
                        BufferedImage.TYPE_INT_RGB
                );

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        ImageIO.write(
                image,
                "png",
                output
        );

        return output.toByteArray();
    }
}