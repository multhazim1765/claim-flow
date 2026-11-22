package com.claire.claims.service.attachment;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentStorageServiceTest {

    @TempDir
    Path tempDir;

    private AttachmentStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new AttachmentStorageService(tempDir.toString(), 10 * 1024 * 1024L);
        storageService.init();
    }

    @Test
    @DisplayName("Stores valid PDF file when magic bytes match")
    void storesValidPdfFile() {
        byte[] pdfBytes = "%PDF-1.4 test document content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "medical_record.pdf", "application/pdf", pdfBytes);

        AttachmentStorageService.StoredFileInfo info = storageService.store(file);

        assertThat(info.storageKey()).endsWith(".dat");
        assertThat(info.sanitizedFileName()).isEqualTo("medical_record.pdf");
        assertThat(info.detectedContentType()).isEqualTo("application/pdf");
        assertThat(info.sizeBytes()).isEqualTo(pdfBytes.length);
        assertThat(info.sha256Hex()).hasSize(64);
    }

    @Test
    @DisplayName("Stores valid PNG file when magic bytes match")
    void storesValidPngFile() {
        byte[] pngHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
        MockMultipartFile file = new MockMultipartFile("file", "lab_scan.png", "image/png", pngHeader);

        AttachmentStorageService.StoredFileInfo info = storageService.store(file);

        assertThat(info.detectedContentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("Rejects executable or shell script masquerading as PDF")
    void rejectsInvalidMagicBytes() {
        byte[] fakePdfBytes = "#!/bin/bash\necho hello".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "exploit.pdf", "application/pdf", fakePdfBytes);

        assertThatThrownBy(() -> storageService.store(file))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("File content signature does not match allowed document types");
    }

    @Test
    @DisplayName("Rejects disallowed file extensions (e.g. .exe, .sh, .zip)")
    void rejectsDisallowedExtension() {
        byte[] content = "dummy".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "virus.exe", "application/octet-stream", content);

        assertThatThrownBy(() -> storageService.store(file))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("is not permitted");
    }

    @Test
    @DisplayName("Sanitizes filename path traversal attempts")
    void sanitizesPathTraversalFilename() {
        byte[] pdfBytes = "%PDF-1.4 clean document".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "../../etc/passwd.pdf", "application/pdf", pdfBytes);

        AttachmentStorageService.StoredFileInfo info = storageService.store(file);

        assertThat(info.sanitizedFileName()).doesNotContain("/");
        assertThat(info.sanitizedFileName()).doesNotContain("..");
    }
}

