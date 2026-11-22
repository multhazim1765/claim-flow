package com.claire.claims.service.attachment;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.common.ApiExceptions.ConflictException;
import com.claire.claims.common.ApiExceptions.NotFoundException;
import com.claire.claims.domain.Claim;
import com.claire.claims.domain.ClaimAttachment;
import com.claire.claims.domain.ClaimAttachmentAudit;
import com.claire.claims.dto.ClaimAttachmentDtos.*;
import com.claire.claims.repository.ClaimAttachmentAuditRepository;
import com.claire.claims.repository.ClaimAttachmentRepository;
import com.claire.claims.repository.ClaimRepository;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ClaimAttachmentService {

    private final ClaimRepository claimRepository;
    private final ClaimAttachmentRepository attachmentRepository;
    private final ClaimAttachmentAuditRepository auditRepository;
    private final AttachmentStorageService storageService;

    private static final Set<String> VALID_CATEGORIES = Set.of(
            "MEDICAL_REPORT",
            "PRESCRIPTION",
            "LAB_REPORT",
            "REFERRAL",
            "PRIOR_AUTHORIZATION",
            "ITEMIZED_BILL",
            "DENIAL_LETTER",
            "APPEAL_SUPPORT",
            "INSURANCE_DOCUMENT",
            "DISCHARGE_SUMMARY",
            "OTHER"
    );

    private static final int MAX_ACTIVE_ATTACHMENTS_PER_CLAIM = 50;

    public ClaimAttachmentService(
            ClaimRepository claimRepository,
            ClaimAttachmentRepository attachmentRepository,
            ClaimAttachmentAuditRepository auditRepository,
            AttachmentStorageService storageService
    ) {
        this.claimRepository = claimRepository;
        this.attachmentRepository = attachmentRepository;
        this.auditRepository = auditRepository;
        this.storageService = storageService;
    }

    @Transactional
    public AttachmentMetadataResponse uploadAttachment(
            Long claimId,
            MultipartFile file,
            String category,
            String description,
            String username
    ) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new NotFoundException("Claim", claimId));

        if (category == null || !VALID_CATEGORIES.contains(category.toUpperCase())) {
            throw new BusinessRuleException("Invalid documentCategory '" + category + "'. Permitted values: " + VALID_CATEGORIES);
        }
        category = category.toUpperCase();

        long activeCount = attachmentRepository.countActiveByClaimId(claimId);
        if (activeCount >= MAX_ACTIVE_ATTACHMENTS_PER_CLAIM) {
            throw new BusinessRuleException("Maximum limit of " + MAX_ACTIVE_ATTACHMENTS_PER_CLAIM + " active attachments per claim reached");
        }

        // Store file on secure private storage
        AttachmentStorageService.StoredFileInfo stored = storageService.store(file);

        ClaimAttachment attachment = new ClaimAttachment();
        attachment.setClaim(claim);
        attachment.setDocumentCategory(category);
        attachment.setOriginalFileName(stored.sanitizedFileName());
        attachment.setStorageKey(stored.storageKey());
        attachment.setContentType(stored.detectedContentType());
        attachment.setFileSize(stored.sizeBytes());
        attachment.setSha256Hash(stored.sha256Hex());
        attachment.setDescription(description);
        attachment.setAttachmentStatus("ACTIVE");
        attachment.setSecurityScanStatus("NOT_SCANNED"); // Synthetic educational demonstration policy
        attachment.setUploadedBy(username != null ? username : "system");
        attachment.setUploadedAt(OffsetDateTime.now());

        ClaimAttachment saved = attachmentRepository.save(attachment);

        // Record audit entry
        recordAudit(saved, claim, "UPLOADED", username, "Uploaded " + stored.sanitizedFileName() + " (" + stored.sizeBytes() + " bytes)");

        return mapToMetadataResponse(saved);
    }

    public List<AttachmentMetadataResponse> listAttachments(Long claimId) {
        if (!claimRepository.existsById(claimId)) {
            throw new NotFoundException("Claim", claimId);
        }
        return attachmentRepository.findByClaimIdOrderByUploadedAtDesc(claimId).stream()
                .map(this::mapToMetadataResponse)
                .toList();
    }

    public AttachmentMetadataResponse getAttachment(Long claimId, Long attachmentId) {
        ClaimAttachment att = findAttachmentScoped(claimId, attachmentId);
        return mapToMetadataResponse(att);
    }

    public record DownloadResult(
            Resource resource,
            String originalFileName,
            String contentType,
            long fileSize
    ) {}

    @Transactional
    public DownloadResult downloadAttachment(Long claimId, Long attachmentId, String username) {
        ClaimAttachment att = findAttachmentScoped(claimId, attachmentId);
        Resource resource = storageService.loadAsResource(att.getStorageKey());

        recordAudit(att, att.getClaim(), "DOWNLOADED", username, "Downloaded document by " + username);

        return new DownloadResult(
                resource,
                att.getOriginalFileName(),
                att.getContentType(),
                att.getFileSize()
        );
    }

    @Transactional
    public AttachmentMetadataResponse archiveAttachment(Long claimId, Long attachmentId, String reason, String username) {
        ClaimAttachment att = findAttachmentScoped(claimId, attachmentId);

        if ("ARCHIVED".equalsIgnoreCase(att.getAttachmentStatus())) {
            throw new ConflictException("Attachment #" + attachmentId + " is already archived");
        }

        att.setAttachmentStatus("ARCHIVED");
        att.setArchivedBy(username != null ? username : "system");
        att.setArchivedAt(OffsetDateTime.now());
        att.setArchiveReason(reason);

        ClaimAttachment saved = attachmentRepository.save(att);

        recordAudit(saved, att.getClaim(), "ARCHIVED", username, "Archived: " + reason);

        return mapToMetadataResponse(saved);
    }

    public List<AttachmentAuditResponse> getAttachmentHistory(Long claimId, Long attachmentId) {
        ClaimAttachment att = findAttachmentScoped(claimId, attachmentId);
        return auditRepository.findByAttachmentIdOrderByActedAtDesc(att.getId()).stream()
                .map(a -> new AttachmentAuditResponse(
                        a.getId(),
                        a.getAttachment().getId(),
                        a.getClaim().getId(),
                        a.getAction(),
                        a.getActor(),
                        a.getActedAt(),
                        a.getNotes()
                ))
                .toList();
    }

    public Page<AttachmentMetadataResponse> searchAttachments(
            String category,
            String status,
            String search,
            Pageable pageable
    ) {
        String cleanCategory = (category != null && !category.isBlank()) ? category.trim() : null;
        String cleanStatus = (status != null && !status.isBlank()) ? status.trim() : null;
        String cleanSearch = (search != null && !search.isBlank()) ? "%" + search.trim().toLowerCase() + "%" : null;

        if (cleanCategory == null && cleanStatus == null && cleanSearch == null) {
            return attachmentRepository.findAll(pageable).map(this::mapToMetadataResponse);
        }

        return attachmentRepository.findFiltered(cleanCategory, cleanStatus, cleanSearch, pageable)
                .map(this::mapToMetadataResponse);
    }

    private ClaimAttachment findAttachmentScoped(Long claimId, Long attachmentId) {
        ClaimAttachment att = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new NotFoundException("ClaimAttachment", attachmentId));

        if (!att.getClaim().getId().equals(claimId)) {
            // Strict cross-claim isolation: return 404 rather than leaking info
            throw new NotFoundException("ClaimAttachment", attachmentId);
        }
        return att;
    }

    private void recordAudit(ClaimAttachment att, Claim claim, String action, String actor, String notes) {
        ClaimAttachmentAudit audit = new ClaimAttachmentAudit();
        audit.setAttachment(att);
        audit.setClaim(claim);
        audit.setAction(action);
        audit.setActor(actor != null ? actor : "system");
        audit.setActedAt(OffsetDateTime.now());
        audit.setNotes(notes);
        auditRepository.save(audit);
    }

    private AttachmentMetadataResponse mapToMetadataResponse(ClaimAttachment a) {
        return new AttachmentMetadataResponse(
                a.getId(),
                a.getClaim().getId(),
                a.getClaim().getClaimNumber(),
                a.getDocumentCategory(),
                a.getOriginalFileName(),
                a.getContentType(),
                a.getFileSize(),
                a.getSha256Hash(),
                a.getDescription(),
                a.getAttachmentStatus(),
                a.getSecurityScanStatus(),
                a.getUploadedBy(),
                a.getUploadedAt(),
                a.getArchivedBy(),
                a.getArchivedAt(),
                a.getArchiveReason(),
                "/api/claims/" + a.getClaim().getId() + "/attachments/" + a.getId() + "/download"
        );
    }
}
