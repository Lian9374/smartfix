package com.smartfix.request.validation;

import com.smartfix.request.config.AttachmentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttachmentValidatorTest {

    private AttachmentProperties properties;
    private AttachmentValidator validator;

    @BeforeEach
    void setUp() {
        properties = new AttachmentProperties();

        properties.setMaxFiles(3);
        properties.setMaxFileSize(5L * 1024 * 1024);
        properties.setMaxTotalSize(15L * 1024 * 1024);
        properties.setMaxDimension(10_000);
        properties.setMaxPixels(20_000_000L);

        validator = new AttachmentValidator(properties);
    }

    @Test
    void acceptsValidPng() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "photo.png",
                "image/png",
                createImage("png", 100, 100)
        );

        assertDoesNotThrow(() ->
                validator.validate(List.of(file))
        );
    }

    @Test
    void acceptsValidJpeg() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "photo.jpg",
                "image/jpeg",
                createImage("jpg", 100, 100)
        );

        assertDoesNotThrow(() ->
                validator.validate(List.of(file))
        );
    }

    @Test
    void rejectsFakePng() {
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "fake.png",
                "image/png",
                "this is not an image".getBytes()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(List.of(file))
        );
    }

    @Test
    void rejectsMoreThanThreeFiles() throws IOException {
        byte[] image = createImage("png", 10, 10);

        MockMultipartFile file1 =
                new MockMultipartFile("files", "1.png",
                        "image/png", image);

        MockMultipartFile file2 =
                new MockMultipartFile("files", "2.png",
                        "image/png", image);

        MockMultipartFile file3 =
                new MockMultipartFile("files", "3.png",
                        "image/png", image);

        MockMultipartFile file4 =
                new MockMultipartFile("files", "4.png",
                        "image/png", image);

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(
                        List.of(file1, file2, file3, file4)
                )
        );
    }

    @Test
    void rejectsFileLargerThanConfiguredLimit() {
        properties.setMaxFileSize(10);

        MockMultipartFile file = new MockMultipartFile(
                "files",
                "large.png",
                "image/png",
                new byte[11]
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(List.of(file))
        );
    }

    @Test
    void rejectsImageWithTooManyPixels() throws IOException {
        properties.setMaxPixels(100);

        MockMultipartFile file = new MockMultipartFile(
                "files",
                "large-dimensions.png",
                "image/png",
                createImage("png", 11, 10)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(List.of(file))
        );
    }

    private byte[] createImage(
            String format,
            int width,
            int height
    ) throws IOException {

        BufferedImage image =
                new BufferedImage(
                        width,
                        height,
                        BufferedImage.TYPE_INT_RGB
                );

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        ImageIO.write(image, format, output);

        return output.toByteArray();
    }
}