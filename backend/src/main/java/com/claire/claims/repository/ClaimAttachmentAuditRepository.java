package com.claire.claims.repository;

import com.claire.claims.domain.ClaimAttachmentAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimAttachmentAuditRepository extends JpaRepository<ClaimAttachmentAudit, Long> {

    List<ClaimAttachmentAudit> findByAttachmentIdOrderByActedAtDesc(Long attachmentId);

    List<ClaimAttachmentAudit> findByClaimIdOrderByActedAtDesc(Long claimId);
}

