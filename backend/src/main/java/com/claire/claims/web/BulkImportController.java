package com.claire.claims.web;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.dto.BulkImportDtos.*;
import com.claire.claims.service.BulkImportService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/import")
public class BulkImportController {

    private final BulkImportService importService;

    public BulkImportController(BulkImportService importService) {
        this.importService = importService;
    }

    @GetMapping("/template/{type}")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER')")
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable String type, Authentication auth) {
        enforceTypePermission(type, auth);
        byte[] csv = importService.getTemplateCsv(type);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + type.toLowerCase() + "_template.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }

    @PostMapping(value = "/preview/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','BILLER')")
    public ResponseEntity<CsvPreviewResponse> previewUpload(
            @PathVariable String type,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        enforceTypePermission(type, auth);
        if (file.isEmpty()) {
            throw new BusinessRuleException("The uploaded file is empty.");
        }
        try {
            CsvPreviewResponse response = importService.previewAndValidate(type, file.getInputStream(), file.getOriginalFilename());
            return ResponseEntity.ok(response);
        } catch (BusinessRuleException bre) {
            throw bre;
        } catch (Exception e) {
            throw new BusinessRuleException("Failed to read CSV: " + e.getMessage());
        }
    }

    @PostMapping(value = "/preview-text/{type}", consumes = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','BILLER')")
    public ResponseEntity<CsvPreviewResponse> previewText(
            @PathVariable String type,
            @RequestBody String csvContent,
            Authentication auth) {
        enforceTypePermission(type, auth);
        InputStream stream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));
        CsvPreviewResponse response = importService.previewAndValidate(type, stream, "text-upload.csv");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/execute/{type}")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER')")
    public ResponseEntity<CsvImportResultResponse> executeImport(
            @PathVariable String type,
            @RequestBody ExecuteImportRequest request,
            Authentication auth) {
        enforceTypePermission(type, auth);
        String username = auth != null ? auth.getName() : "system";
        CsvImportResultResponse result = importService.executeImport(type, request, username);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/batches")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<ImportBatchSummaryResponse>> listBatches(
            @RequestParam String type,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(importService.listBatches(type, pageable));
    }

    private void enforceTypePermission(String type, Authentication auth) {
        String upper = type.toUpperCase().trim();
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && (upper.equals("PROVIDERS") || upper.equals("PAYERS"))) {
            throw new AccessDeniedException("Only administrators can import payers or providers.");
        }
    }
}

