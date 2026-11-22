package com.claire.claims.service;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.common.ApiExceptions.ConflictException;
import com.claire.claims.common.ApiExceptions.NotFoundException;
import com.claire.claims.domain.Claim;
import com.claire.claims.domain.ClaimAttachment;
import com.claire.claims.domain.ClaimAttachmentAudit;
import com.claire.claims.dto.ClaimAttachmentDtos.AttachmentMetadataResponse;
import com.claire.claims.repository.ClaimAttachmentAuditRepository;
import com.claire.claims.repository.ClaimAttachmentRepository;
import com.claire.claims.repository.ClaimRepository;
import com.claire.claims.service.attachment.AttachmentStorageService;
import com.claire.claims.service.attachment.ClaimAttachmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimAttachmentServiceTest {

    @Mock private ClaimRepository claimRepository;
    @Mock private ClaimAttachmentRepository attachmentRepository;
    @Mock private ClaimAttachmentAuditRepository auditRepository;
    @Mock private AttachmentStorageService storageService;

    private ClaimAttachmentService service;
    private Claim claim;

    @BeforeEach
    void setUp() {
        service = new ClaimAttachmentService(claimRepository, attachmentRepository, auditRepository, storageService);

        claim = new Claim();
        claim.setId(10L);
        claim.setClaimNumber("CLM-10");
    }

    @Test
    @DisplayName("Uploads attachment and logs UPLOADED audit entry")
    void uploadAttachmentSuccess() {
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));
        when(attachmentRepository.countActiveByClaimId(10L)).thenReturn(0L);

        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "%PDF-1.4 test".getBytes());
        when(storageService.store(file)).thenReturn(new AttachmentStorageService.StoredFileInfo(
                "key-1.dat", "test.pdf", "application/pdf", 14L, "hash123"
        ));

        when(attachmentRepository.save(any(ClaimAttachment.class))).thenAnswer(inv -> {
            ClaimAttachment a = inv.getArgument(0);
            a.setId(50L);
            return a;
        });

        AttachmentMetadataResponse response = service.uploadAttachment(10L, file, "MEDICAL_REPORT", "Doctor notes", "biller");

        assertThat(response.id()).isEqualTo(50L);
        assertThat(response.claimId()).isEqualTo(10L);
        assertThat(response.documentCategory()).isEqualTo("MEDICAL_REPORT");
        assertThat(response.originalFileName()).isEqualTo("test.pdf");
        assertThat(response.attachmentStatus()).isEqualTo("ACTIVE");

        verify(auditRepository).save(argThat(audit -> "UPLOADED".equals(audit.getAction())));
    }

    @Test
    @DisplayName("Rejects upload with invalid document category")
    void uploadAttachmentRejectsInvalidCategory() {
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "%PDF-1.4 test".getBytes());

        assertThatThrownBy(() -> service.uploadAttachment(10L, file, "INVALID_CAT", "Notes", "biller"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Invalid documentCategory");
    }

    @Test
    @DisplayName("Cross-claim isolation: cannot access attachment belonging to claim 20 via claim 10")
    void crossClaimIsolationEnforced() {
        Claim claim20 = new Claim();
        claim20.setId(20L);

        ClaimAttachment att = new ClaimAttachment();
        att.setId(50L);
        att.setClaim(claim20);

        when(attachmentRepository.findById(50L)).thenReturn(Optional.of(att));

        assertThatThrownBy(() -> service.getAttachment(10L, 50L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Archives attachment and logs ARCHIVED audit entry")
    void archiveAttachmentSuccess() {
        ClaimAttachment att = new ClaimAttachment();
        att.setId(50L);
        att.setClaim(claim);
        att.setAttachmentStatus("ACTIVE");
        att.setOriginalFileName("doc.pdf");

        when(attachmentRepository.findById(50L)).thenReturn(Optional.of(att));
        when(attachmentRepository.save(any(ClaimAttachment.class))).thenAnswer(inv -> inv.getArgument(0));

        AttachmentMetadataResponse response = service.archiveAttachment(10L, 50L, "Replaced by newer version", "biller");

        assertThat(response.attachmentStatus()).isEqualTo("ARCHIVED");
        assertThat(response.archiveReason()).isEqualTo("Replaced by newer version");
        verify(auditRepository).save(argThat(audit -> "ARCHIVED".equals(audit.getAction())));
    }

    @Test
    @DisplayName("Cannot re-archive an already archived attachment")
    void cannotReArchiveAttachment() {
        ClaimAttachment att = new ClaimAttachment();
        att.setId(50L);
        att.setClaim(claim);
        att.setAttachmentStatus("ARCHIVED");

        when(attachmentRepository.findById(50L)).thenReturn(Optional.of(att));

        assertThatThrownBy(() -> service.archiveAttachment(10L, 50L, "Again", "biller"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("is already archived");
    }
}

