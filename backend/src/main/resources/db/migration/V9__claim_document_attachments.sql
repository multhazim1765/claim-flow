-- =====================================================================
-- Migration V9: Secure Claim Document Attachments
-- =====================================================================

CREATE TABLE claim_attachment (
    id BIGSERIAL PRIMARY KEY,
    claim_id BIGINT NOT NULL REFERENCES claim (id) ON DELETE CASCADE,
    document_category VARCHAR(40) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(512) NOT NULL UNIQUE,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256_hash CHAR(64) NOT NULL,
    description VARCHAR(1000),
    attachment_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    security_scan_status VARCHAR(30) NOT NULL DEFAULT 'NOT_SCANNED',
    uploaded_by VARCHAR(60) NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    archived_by VARCHAR(60),
    archived_at TIMESTAMPTZ,
    archive_reason VARCHAR(500),
    CONSTRAINT ck_att_status CHECK (attachment_status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_att_scan_status CHECK (security_scan_status IN ('NOT_SCANNED', 'CLEAN', 'FLAGGED')),
    CONSTRAINT ck_att_category CHECK (document_category IN (
        'MEDICAL_REPORT',
        'PRESCRIPTION',
        'LAB_REPORT',
        'REFERRAL',
        'PRIOR_AUTHORIZATION',
        'ITEMIZED_BILL',
        'DENIAL_LETTER',
        'APPEAL_SUPPORT',
        'INSURANCE_DOCUMENT',
        'DISCHARGE_SUMMARY',
        'OTHER'
    ))
);

CREATE TABLE claim_attachment_audit (
    id BIGSERIAL PRIMARY KEY,
    attachment_id BIGINT NOT NULL REFERENCES claim_attachment (id) ON DELETE CASCADE,
    claim_id BIGINT NOT NULL REFERENCES claim (id) ON DELETE CASCADE,
    action VARCHAR(30) NOT NULL,
    actor VARCHAR(60) NOT NULL,
    acted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    notes VARCHAR(500),
    CONSTRAINT ck_att_audit_action CHECK (action IN ('UPLOADED', 'DOWNLOADED', 'VIEWED', 'ARCHIVED', 'RESTORED'))
);

CREATE INDEX ix_claim_att_claim ON claim_attachment (claim_id);
CREATE INDEX ix_claim_att_category ON claim_attachment (document_category);
CREATE INDEX ix_claim_att_status ON claim_attachment (attachment_status);
CREATE INDEX ix_claim_att_uploaded ON claim_attachment (uploaded_at DESC);

CREATE INDEX ix_claim_att_audit_att ON claim_attachment_audit (attachment_id);
CREATE INDEX ix_claim_att_audit_claim ON claim_attachment_audit (claim_id);
CREATE INDEX ix_claim_att_audit_acted ON claim_attachment_audit (acted_at DESC);

