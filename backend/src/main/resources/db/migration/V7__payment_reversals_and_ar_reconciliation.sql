-- =====================================================================
-- Migration V7: Payment Reversals, Adjustment Reversals & Remittance Exception Resolution
-- =====================================================================

-- 1. Support Contractual Adjustment Reversal on claim_payment_ledger
ALTER TABLE claim_payment_ledger ALTER COLUMN entry_type TYPE VARCHAR(50);

ALTER TABLE claim_payment_ledger DROP CONSTRAINT IF EXISTS ck_ledger_entry_type;

ALTER TABLE claim_payment_ledger ADD CONSTRAINT ck_ledger_entry_type
    CHECK (entry_type IN (
        'LEGACY_INITIAL_BALANCE',
        'PAYER_PAYMENT',
        'PAYER_REVERSAL',
        'CONTRACTUAL_WRITE_OFF',
        'CONTRACTUAL_ADJUSTMENT_REVERSAL',
        'PATIENT_PAYMENT',
        'PATIENT_ADJUSTMENT'
    ));

ALTER TABLE claim_payment_ledger DROP CONSTRAINT IF EXISTS ck_ledger_amount_sign;

ALTER TABLE claim_payment_ledger ADD CONSTRAINT ck_ledger_amount_sign CHECK (
    amount <> 0.00 AND (
        (entry_type IN ('PAYER_REVERSAL', 'CONTRACTUAL_ADJUSTMENT_REVERSAL', 'PATIENT_ADJUSTMENT') AND amount < 0) OR
        (entry_type IN ('LEGACY_INITIAL_BALANCE', 'PAYER_PAYMENT', 'CONTRACTUAL_WRITE_OFF', 'PATIENT_PAYMENT') AND amount > 0)
    )
);

-- Unique index to prevent duplicate contractual adjustment reversals against the same original payment
CREATE UNIQUE INDEX IF NOT EXISTS uq_ledger_single_co_reversal
    ON claim_payment_ledger (original_ledger_id, entry_type)
    WHERE original_ledger_id IS NOT NULL AND entry_type = 'CONTRACTUAL_ADJUSTMENT_REVERSAL';

-- Index for efficient filtered ledger audit queries by claim and type
CREATE INDEX IF NOT EXISTS ix_ledger_claim_entry
    ON claim_payment_ledger (claim_id, entry_type);

-- 2. Audit and Resolution columns for remittance_claim_exception
ALTER TABLE remittance_claim_exception
    ADD COLUMN IF NOT EXISTS resolution_action VARCHAR(50),
    ADD COLUMN IF NOT EXISTS resolution_notes VARCHAR(500),
    ADD COLUMN IF NOT EXISTS matched_claim_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_remit_exc_matched_claim'
    ) THEN
        ALTER TABLE remittance_claim_exception
            ADD CONSTRAINT fk_remit_exc_matched_claim
            FOREIGN KEY (matched_claim_id) REFERENCES claim (id) ON DELETE RESTRICT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS ix_remit_exc_status_type
    ON remittance_claim_exception (status, exception_type);
