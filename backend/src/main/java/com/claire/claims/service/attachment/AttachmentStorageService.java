package com.claire.claims.service.attachment;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

@Service
public class AttachmentStorageService {

    private final Path storageDirectory;
    private final long maxFileSize;

    // Allowed extensions
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png");

    // Allowed MIME types
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png"
    );

    // Magic numbers
    private static final byte[] PDF_MAGIC = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D}; // %PDF-
    private static final byte[] JPEG_MAGIC = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    public record StoredFileInfo(
            String storageKey,
            String sanitizedFileName,
            String detectedContentType,
            long sizeBytes,
            String sha256Hex
    ) {}

    public AttachmentStorageService(
            @Value("${app.attachments.storage-dir:${ATTACHMENT_STORAGE_DIR:/app/data/attachments}}") String dirPath,
            @Value("${app.attachments.max-file-size:10485760}") long maxFileSize // Default 10 MB
    ) {
        this.storageDirectory = Paths.get(dirPath).toAbsolutePath().normalize();
        this.maxFileSize = maxFileSize;
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(storageDirectory);
        } catch (IOException e) {
            // In local development or restricted environments, fallback to working directory
            try {
                Path fallback = Paths.get("./data/attachments").toAbsolutePath().normalize();
                Files.createDirectories(fallback);
            } catch (IOException ex) {
                throw new RuntimeException("Could not initialize attachment storage directory: " + storageDirectory, e);
            }
        }
    }

    public StoredFileInfo store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Attachment file must not be empty");
        }

        if (file.getSize() > maxFileSize) {
            throw new BusinessRuleException("File exceeds maximum allowed size of " + (maxFileSize / (1024 * 1024)) + " MB");
        }

        String originalName = sanitizeFilename(file.getOriginalFilename());
        String ext = getExtension(originalName).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessRuleException("File extension '." + ext + "' is not permitted. Only PDF, JPG, and PNG documents are allowed.");
        }

        // Validate content sniffing / magic bytes
        String detectedContentType = validateMagicBytes(file, ext);

        // Generate randomized storage key
        String storageKey = UUID.randomUUID() + ".dat";
        Path targetPath = getResolvedPath(storageKey);

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }

        try (InputStream is = file.getInputStream();
             DigestInputStream dis = new DigestInputStream(is, md)) {
            // Ensure target dir exists
            Files.createDirectories(targetPath.getParent());
            Files.copy(dis, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            deleteIfExists(targetPath);
            throw new RuntimeException("Failed to store uploaded attachment file", e);
        }

        byte[] digest = md.digest();
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            String h = Integer.toHexString(0xff & b);
            if (h.length() == 1) hex.append('0');
            hex.append(h);
        }

        return new StoredFileInfo(
                storageKey,
                originalName,
                detectedContentType,
                file.getSize(),
                hex.toString()
        );
    }

    public Resource loadAsResource(String storageKey) {
        Path filePath = getResolvedPath(storageKey);
        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            throw new BusinessRuleException("Attachment file not found on storage");
        }
        return new FileSystemResource(filePath);
    }

    public void delete(String storageKey) {
        Path filePath = getResolvedPath(storageKey);
        deleteIfExists(filePath);
    }

    private Path getResolvedPath(String storageKey) {
        Path path = storageDirectory.resolve(storageKey).normalize();
        if (!path.startsWith(storageDirectory)) {
            throw new SecurityException("Storage path traversal attempted: " + storageKey);
        }
        return path;
    }

    private void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {}
    }

    private String validateMagicBytes(MultipartFile file, String extension) {
        byte[] header = new byte[8];
        try (InputStream is = file.getInputStream()) {
            int read = is.read(header);
            if (read < 3) {
                throw new BusinessRuleException("Uploaded file is corrupted or too small to verify");
            }
        } catch (IOException e) {
            throw new BusinessRuleException("Failed to read uploaded file signature: " + e.getMessage());
        }

        if (startsWith(header, PDF_MAGIC)) {
            return "application/pdf";
        } else if (startsWith(header, JPEG_MAGIC)) {
            return "image/jpeg";
        } else if (startsWith(header, PNG_MAGIC)) {
            return "image/png";
        }

        throw new BusinessRuleException("File content signature does not match allowed document types (PDF, JPEG, PNG). Executables, scripts, and archives are prohibited.");
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    private static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "unnamed_document";
        }
        // Strip path traversal sequences and illegal characters
        String clean = filename.replace("\\", "/");
        int lastSlash = clean.lastIndexOf('/');
        if (lastSlash >= 0) {
            clean = clean.substring(lastSlash + 1);
        }
        clean = clean.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (clean.isBlank()) clean = "document";
        return clean;
    }

    private static String getExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) return "";
        return filename.substring(idx + 1);
    }
}

