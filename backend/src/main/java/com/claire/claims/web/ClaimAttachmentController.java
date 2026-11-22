package com.claire.claims.web;

import com.claire.claims.dto.ClaimAttachmentDtos.*;
import com.claire.claims.service.attachment.ClaimAttachmentService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
public class ClaimAttachmentController {

    private final ClaimAttachmentService attachmentService;

    public ClaimAttachmentController(ClaimAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /**
     * Uploads a supporting document against a claim with magic bytes validation and private storage.
     */
    @PostMapping(value = "/api/claims/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','BILLER')")
    public ResponseEntity<AttachmentMetadataResponse> uploadAttachment(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentCategory") String documentCategory,
            @RequestParam(value = "description", required = false) String description,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String username = jwt != null ? jwt.getSubject() : "anonymous";
        AttachmentMetadataResponse response = attachmentService.uploadAttachment(id, file, documentCategory, description, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lists all active attachment metadata for a claim.
     */
    @GetMapping("/api/claims/{id}/attachments")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER','VIEWER')")
    public ResponseEntity<List<AttachmentMetadataResponse>> listAttachments(@PathVariable Long id) {
        return ResponseEntity.ok(attachmentService.listAttachments(id));
    }

    /**
     * Retrieves specific attachment metadata for a claim.
     */
    @GetMapping("/api/claims/{id}/attachments/{attachmentId}")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER','VIEWER')")
    public ResponseEntity<AttachmentMetadataResponse> getAttachment(
            @PathVariable Long id,
            @PathVariable Long attachmentId
    ) {
        return ResponseEntity.ok(attachmentService.getAttachment(id, attachmentId));
    }

    /**
     * Downloads an authorized document attachment with security headers.
     */
    @GetMapping("/api/claims/{id}/attachments/{attachmentId}/download")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER')")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable Long id,
            @PathVariable Long attachmentId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String username = jwt != null ? jwt.getSubject() : "anonymous";
        ClaimAttachmentService.DownloadResult result = attachmentService.downloadAttachment(id, attachmentId, username);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.originalFileName() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, result.contentType())
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(result.fileSize()))
                .header("X-Content-Type-Options", "nosniff")
                .body(result.resource());
    }

    /**
     * Logically archives a claim document with a mandatory audit reason.
     */
    @PostMapping("/api/claims/{id}/attachments/{attachmentId}/archive")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER')")
    public ResponseEntity<AttachmentMetadataResponse> archiveAttachment(
            @PathVariable Long id,
            @PathVariable Long attachmentId,
            @Valid @RequestBody ArchiveAttachmentRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String username = jwt != null ? jwt.getSubject() : "anonymous";
        AttachmentMetadataResponse response = attachmentService.archiveAttachment(id, attachmentId, request.archiveReason(), username);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves audit history for an attachment.
     */
    @GetMapping("/api/claims/{id}/attachments/{attachmentId}/history")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER')")
    public ResponseEntity<List<AttachmentAuditResponse>> getAttachmentHistory(
            @PathVariable Long id,
            @PathVariable Long attachmentId
    ) {
        return ResponseEntity.ok(attachmentService.getAttachmentHistory(id, attachmentId));
    }

    /**
     * System-wide document attachment search and worklist across claims.
     */
    @GetMapping("/api/attachments")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER','VIEWER')")
    public ResponseEntity<Page<AttachmentMetadataResponse>> searchAttachments(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(attachmentService.searchAttachments(category, status, search, pageable));
    }
}

