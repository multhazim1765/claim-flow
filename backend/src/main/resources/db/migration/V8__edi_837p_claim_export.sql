-- =====================================================================
-- Migration V8: EDI 837P Professional Claim Export
-- =====================================================================

CREATE TABLE edi_export_batch (
    id BIGSERIAL PRIMARY KEY,
    batch_number VARCHAR(32) NOT NULL UNIQUE,
    payer_id BIGINT REFERENCES payer (id) ON DELETE RESTRICT,
    interchange_control_number VARCHAR(9) NOT NULL,
    group_control_number VARCHAR(9) NOT NULL,
    transaction_control_number VARCHAR(9) NOT NULL,
    export_status VARCHAR(20) NOT NULL DEFAULT 'EXPORTED',
    claim_count INTEGER NOT NULL,
    total_billed_amount NUMERIC(12,2) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    created_by VARCHAR(60) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    exported_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    validation_summary TEXT,
    CONSTRAINT ck_export_status CHECK (export_status IN ('VALIDATED', 'EXPORTED', 'FAILED'))
);

CREATE TABLE edi_export_batch_claim (
    id BIGSERIAL PRIMARY KEY,
    export_batch_id BIGINT NOT NULL REFERENCES edi_export_batch (id) ON DELETE CASCADE,
    claim_id BIGINT NOT NULL REFERENCES claim (id) ON DELETE RESTRICT,
    claim_number_snapshot VARCHAR(24) NOT NULL,
    claim_charge_snapshot NUMERIC(12,2) NOT NULL,
    transaction_sequence INTEGER NOT NULL,
    exported_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_export_batch_claim UNIQUE (export_batch_id, claim_id)
);

CREATE INDEX ix_edi_batch_payer ON edi_export_batch (payer_id);
CREATE INDEX ix_edi_batch_created ON edi_export_batch (created_at DESC);
CREATE INDEX ix_edi_batch_status ON edi_export_batch (export_status);

CREATE INDEX ix_edi_batch_claim_batch ON edi_export_batch_claim (export_batch_id);
CREATE INDEX ix_edi_batch_claim_claim ON edi_export_batch_claim (claim_id);

