-- =====================================================================
-- Migration V10: Complete Master Data Management and Bulk Import Audit
-- =====================================================================

-- Add active status flag to patient table for soft archival and filtering
ALTER TABLE patient ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;
CREATE INDEX IF NOT EXISTS ix_patient_active ON patient (active);

-- Add active status flag to insurance_policy table for policy deactivation
ALTER TABLE insurance_policy ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;
CREATE INDEX IF NOT EXISTS ix_policy_active ON insurance_policy (active);

-- Table for tracking CSV bulk import executions and results
CREATE TABLE IF NOT EXISTS data_import_batch (
    id             BIGSERIAL PRIMARY KEY,
    entity_type    VARCHAR(30) NOT NULL,
    filename       VARCHAR(255) NOT NULL,
    total_rows     INTEGER NOT NULL,
    success_count  INTEGER NOT NULL,
    error_count    INTEGER NOT NULL,
    status         VARCHAR(20) NOT NULL,
    imported_by    VARCHAR(60) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    error_summary  TEXT,
    CONSTRAINT ck_import_batch_status CHECK (status IN ('COMPLETED', 'FAILED', 'PARTIAL')),
    CONSTRAINT ck_import_batch_entity CHECK (entity_type IN ('PATIENTS', 'PROVIDERS', 'PAYERS', 'POLICIES'))
);

CREATE INDEX IF NOT EXISTS ix_import_batch_type_created ON data_import_batch (entity_type, created_at DESC);

