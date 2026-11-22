package com.claire.claims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public final class ClaimAttachmentDtos {

    private ClaimAttachmentDtos() {}

    public record AttachmentMetadataResponse(
            Long id,
            Long claimId,
            String claimNumber,
            String documentCategory,
            String originalFileName,
            String contentType,
            long fileSize,
            String sha256Hash,
            String description,
            String attachmentStatus,
            String securityScanStatus,
            String uploadedBy,
            OffsetDateTime uploadedAt,
            String archivedBy,
            OffsetDateTime archivedAt,
            String archiveReason,
            String downloadUrl
    ) {}

    public record AttachmentAuditResponse(
            Long id,
            Long attachmentId,
            Long claimId,
            String action,
            String actor,
            OffsetDateTime actedAt,
            String notes
    ) {}

    public record ArchiveAttachmentRequest(
            @NotBlank(message = "archiveReason is required")
            @Size(max = 500, message = "archiveReason must not exceed 500 characters")
            String archiveReason
    ) {}
}

