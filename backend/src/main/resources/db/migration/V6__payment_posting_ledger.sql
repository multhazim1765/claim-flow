-- =====================================================================
-- Migration V6: Immutable Claim Payment Ledger, Audit & Baseline Backfill
-- =====================================================================

-- 1. Explicit Contractual Adjustment Column on Claim
ALTER TABLE claim ADD COLUMN IF NOT EXISTS contractual_adjustment NUMERIC(12,2) NOT NULL DEFAULT 0.00;

-- 2. Append-Only Financial Payment Ledger Table
CREATE TABLE claim_payment_ledger (
    id                   BIGSERIAL PRIMARY KEY,
    claim_id             BIGINT         NOT NULL,
    voucher_id           BIGINT,                                    -- NULL for legacy baseline entries
    remittance_id        BIGINT,                                    -- NULL for legacy baseline entries
    entry_type           VARCHAR(30)    NOT NULL,
    amount               NUMERIC(12,2)  NOT NULL,
    payer_icn            VARCHAR(50),
    check_number         VARCHAR(50),
    check_date           DATE,
    original_ledger_id   BIGINT,                                    -- Self-reference reserved for future reversals
    notes                VARCHAR(500),
    posted_by            VARCHAR(60)    NOT NULL,
    posted_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_ledger_claim
        FOREIGN KEY (claim_id) REFERENCES claim (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ledger_voucher
        FOREIGN KEY (voucher_id) REFERENCES remittance_claim_voucher (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ledger_remittance
        FOREIGN KEY (remittance_id) REFERENCES remittance_batch (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ledger_original
        FOREIGN KEY (original_ledger_id) REFERENCES claim_payment_ledger (id) ON DELETE RESTRICT,

    CONSTRAINT ck_ledger_entry_type
        CHECK (entry_type IN (
            'LEGACY_INITIAL_BALANCE',
            'PAYER_PAYMENT',
            'PAYER_REVERSAL',
            'CONTRACTUAL_WRITE_OFF',
            'PATIENT_PAYMENT',
            'PATIENT_ADJUSTMENT'
        )),

    -- Sign Integrity Checks: Non-zero; positive for credits/payments, negative for reversals
    CONSTRAINT ck_ledger_amount_sign CHECK (
        amount <> 0.00 AND (
            (entry_type IN ('PAYER_REVERSAL', 'PATIENT_ADJUSTMENT') AND amount < 0) OR
            (entry_type IN ('LEGACY_INITIAL_BALANCE', 'PAYER_PAYMENT', 'CONTRACTUAL_WRITE_OFF', 'PATIENT_PAYMENT') AND amount > 0)
        )
    )
);

-- Fast Audit & History Queries
CREATE INDEX ix_ledger_claim       ON claim_payment_ledger (claim_id);
CREATE INDEX ix_ledger_voucher     ON claim_payment_ledger (voucher_id);
CREATE INDEX ix_ledger_remittance  ON claim_payment_ledger (remittance_id);
CREATE INDEX ix_ledger_posted_at   ON claim_payment_ledger (posted_at);

-- Prevent Duplicate Voucher Posting per Entry Type
CREATE UNIQUE INDEX uq_ledger_voucher_entry
    ON claim_payment_ledger (voucher_id, entry_type)
    WHERE voucher_id IS NOT NULL;

-- Prevent Duplicate Baseline Initial Entries per Claim
CREATE UNIQUE INDEX uq_ledger_legacy_baseline
    ON claim_payment_ledger (claim_id)
    WHERE entry_type = 'LEGACY_INITIAL_BALANCE';

-- Prevent Duplicate Reversals Against the Same Original Payment
CREATE UNIQUE INDEX uq_ledger_single_reversal
    ON claim_payment_ledger (original_ledger_id, entry_type)
    WHERE original_ledger_id IS NOT NULL AND entry_type = 'PAYER_REVERSAL';

-- 3. One-Time Historical Backfill: Preserve Pre-Existing Phase 1 Payments
-- Uses existing claim created_at as provenance date, without inferring contractual write-offs.
INSERT INTO claim_payment_ledger (
    claim_id,
    entry_type,
    amount,
    notes,
    posted_by,
    posted_at
)
SELECT 
    c.id,
    'LEGACY_INITIAL_BALANCE',
    c.paid_amount,
    'Phase 1 legacy baseline payment balance',
    'system_v6_migration',
    c.created_at
FROM claim c
WHERE c.paid_amount > 0;

