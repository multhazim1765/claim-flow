-- =====================================================================
-- ClaimFlow Milestone 2.3: Payment Reconciliation Schema
-- Synthetic ERA 835 Remittance Import, Payment Posting & Reconciliation
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Table: remittance_batch
-- Ingested ERA 835 batch headers, check/EFT trace details and totals
-- ---------------------------------------------------------------------
CREATE TABLE remittance_batch (
    id                   BIGSERIAL PRIMARY KEY,
    batch_control_number VARCHAR(50)   NOT NULL UNIQUE,
    payer_id             BIGINT        NOT NULL,
    payment_method       VARCHAR(10)   NOT NULL DEFAULT 'ACH', -- ACH, CHK, NON
    check_number         VARCHAR(50)   NOT NULL,               -- Check or EFT Trace Number
    check_date           DATE          NOT NULL,
    total_paid           NUMERIC(12,2) NOT NULL,
    claims_count         INTEGER       NOT NULL DEFAULT 0,
    matched_count        INTEGER       NOT NULL DEFAULT 0,
    exception_count      INTEGER       NOT NULL DEFAULT 0,
    status               VARCHAR(30)   NOT NULL DEFAULT 'VALIDATED',
    raw_payload          TEXT,                                 -- Redacted/synthetic payload for audit
    imported_by          VARCHAR(60)   NOT NULL,
    imported_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_remittance_batch_payer
        FOREIGN KEY (payer_id) REFERENCES payer (id) ON DELETE RESTRICT,
    CONSTRAINT ck_remittance_batch_status
        CHECK (status IN ('VALIDATED', 'OUT_OF_BALANCE', 'PARTIALLY_POSTED', 'POSTED', 'REVERSED')),
    CONSTRAINT ck_remittance_batch_method
        CHECK (payment_method IN ('ACH', 'CHK', 'NON')),
    CONSTRAINT uq_remittance_payer_check_date
        UNIQUE (payer_id, check_number, check_date)
);

CREATE INDEX ix_remittance_batch_payer  ON remittance_batch (payer_id);
CREATE INDEX ix_remittance_batch_date   ON remittance_batch (check_date);
CREATE INDEX ix_remittance_batch_status ON remittance_batch (status);

-- ---------------------------------------------------------------------
-- 2. Table: remittance_claim_voucher
-- Parsed CLP segments staged for review and subsequent payment posting
-- ---------------------------------------------------------------------
CREATE TABLE remittance_claim_voucher (
    id                     BIGSERIAL PRIMARY KEY,
    remittance_id          BIGINT        NOT NULL,
    claim_id               BIGINT,                                                 -- NULL if claim is unmatched
    sequence_no            INTEGER       NOT NULL,                                 -- 1-based index in batch
    claim_number           VARCHAR(24)   NOT NULL,                                 -- CLP01
    payer_icn              VARCHAR(50),                                            -- CLP07 Internal Control Number
    clp_status_code        VARCHAR(5)    NOT NULL,                                 -- 1, 2, 3, 4, 19, 20, 21, 22
    billed_amount          NUMERIC(12,2) NOT NULL,                                 -- CLP03
    allowed_amount         NUMERIC(12,2) NOT NULL,
    paid_amount            NUMERIC(12,2) NOT NULL,                                 -- CLP04 (Signed for reversals)
    patient_responsibility NUMERIC(12,2) NOT NULL DEFAULT 0.00,                   -- CLP05
    contractual_adj        NUMERIC(12,2) NOT NULL DEFAULT 0.00,                   -- Sum of CO adjustments
    other_adj              NUMERIC(12,2) NOT NULL DEFAULT 0.00,                   -- Sum of OA/PI adjustments
    entry_type             VARCHAR(20)   NOT NULL DEFAULT 'ORIGINAL',             -- ORIGINAL, REVERSAL, CORRECTION
    balance_status         VARCHAR(20)   NOT NULL DEFAULT 'BALANCED',             -- BALANCED, OUT_OF_BALANCE
    posting_status         VARCHAR(20)   NOT NULL DEFAULT 'UNPOSTED',             -- UNPOSTED, POSTED, FAILED, EXCLUDED
    original_voucher_id    BIGINT,                                                 -- Self-reference for reversals
    posted_at              TIMESTAMPTZ,
    posted_by              VARCHAR(60),

    CONSTRAINT fk_voucher_remittance
        FOREIGN KEY (remittance_id) REFERENCES remittance_batch (id) ON DELETE RESTRICT,
    CONSTRAINT fk_voucher_claim
        FOREIGN KEY (claim_id) REFERENCES claim (id) ON DELETE RESTRICT,
    CONSTRAINT fk_voucher_original
        FOREIGN KEY (original_voucher_id) REFERENCES remittance_claim_voucher (id) ON DELETE RESTRICT,
    CONSTRAINT ck_voucher_entry_type
        CHECK (entry_type IN ('ORIGINAL', 'REVERSAL', 'CORRECTION')),
    CONSTRAINT ck_voucher_balance_status
        CHECK (balance_status IN ('BALANCED', 'OUT_OF_BALANCE')),
    CONSTRAINT ck_voucher_posting_status
        CHECK (posting_status IN ('UNPOSTED', 'POSTED', 'FAILED', 'EXCLUDED')),
    CONSTRAINT uq_voucher_seq
        UNIQUE (remittance_id, sequence_no)
);

CREATE INDEX ix_voucher_claim  ON remittance_claim_voucher (claim_id);
CREATE INDEX ix_voucher_remit  ON remittance_claim_voucher (remittance_id);
CREATE INDEX ix_voucher_status ON remittance_claim_voucher (posting_status);

-- Composite unique index supporting voucher idempotency even when payer ICN is null or blank
CREATE UNIQUE INDEX ix_uq_remittance_claim_icn
    ON remittance_claim_voucher (remittance_id, claim_number, COALESCE(payer_icn, 'NONE'), entry_type);

-- ---------------------------------------------------------------------
-- 3. Table: remittance_adjustment
-- Claim-level and service-line level adjustments (CARC and RARC codes)
-- ---------------------------------------------------------------------
CREATE TABLE remittance_adjustment (
    id            BIGSERIAL PRIMARY KEY,
    voucher_id    BIGINT        NOT NULL,
    claim_id      BIGINT,                                   -- Denormalized claim ref for fast claim audit lookup
    claim_line_id BIGINT,                                   -- NULL = Claim-level adjustment, non-NULL = Line-level
    group_code    VARCHAR(2)    NOT NULL,                   -- CO, PR, OA, PI
    reason_code   VARCHAR(10)   NOT NULL,                   -- CARC (e.g. 45, 1, 2, 3, 197)
    remark_code   VARCHAR(10),                              -- RARC (e.g. N130, M15)
    amount        NUMERIC(12,2) NOT NULL,                   -- Signed to support reversals/debits
    entry_type    VARCHAR(20)   NOT NULL DEFAULT 'ORIGINAL',

    CONSTRAINT fk_remit_adj_voucher
        FOREIGN KEY (voucher_id) REFERENCES remittance_claim_voucher (id) ON DELETE RESTRICT,
    CONSTRAINT fk_remit_adj_claim
        FOREIGN KEY (claim_id) REFERENCES claim (id) ON DELETE RESTRICT,
    CONSTRAINT fk_remit_adj_line
        FOREIGN KEY (claim_line_id) REFERENCES claim_line (id) ON DELETE RESTRICT,
    CONSTRAINT ck_remit_adj_group
        CHECK (group_code IN ('CO', 'PR', 'OA', 'PI')),
    CONSTRAINT ck_remit_adj_type
        CHECK (entry_type IN ('ORIGINAL', 'REVERSAL', 'CORRECTION'))
);

CREATE INDEX ix_remit_adj_voucher ON remittance_adjustment (voucher_id);
CREATE INDEX ix_remit_adj_claim   ON remittance_adjustment (claim_id);
CREATE INDEX ix_remit_adj_line    ON remittance_adjustment (claim_line_id);

-- ---------------------------------------------------------------------
-- 4. Table: remittance_claim_exception
-- Unmatched claims, payer/charge discrepancies, and out-of-balance vouchers
-- ---------------------------------------------------------------------
CREATE TABLE remittance_claim_exception (
    id             BIGSERIAL PRIMARY KEY,
    remittance_id  BIGINT        NOT NULL,
    voucher_id     BIGINT,                                  -- Associated voucher if parsed
    claim_number   VARCHAR(50)   NOT NULL,
    patient_name   VARCHAR(120),
    service_date   DATE,
    billed_amount  NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    paid_amount    NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    exception_type VARCHAR(30)   NOT NULL,                  -- UNMATCHED_CLAIM, PAYER_MISMATCH, CHARGE_MISMATCH, OUT_OF_BALANCE, INCOMPATIBLE_STATUS
    error_message  VARCHAR(500)  NOT NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'UNRESOLVED',
    resolved_at    TIMESTAMPTZ,
    resolved_by    VARCHAR(60),

    CONSTRAINT fk_remit_exc_batch
        FOREIGN KEY (remittance_id) REFERENCES remittance_batch (id) ON DELETE RESTRICT,
    CONSTRAINT fk_remit_exc_voucher
        FOREIGN KEY (voucher_id) REFERENCES remittance_claim_voucher (id) ON DELETE RESTRICT,
    CONSTRAINT ck_remit_exc_status
        CHECK (status IN ('UNRESOLVED', 'RESOLVED', 'IGNORED'))
);

CREATE INDEX ix_remit_exc_batch  ON remittance_claim_exception (remittance_id);
CREATE INDEX ix_remit_exc_status ON remittance_claim_exception (status);

-- ---------------------------------------------------------------------
-- 5. Table: payment_posting_ledger
-- Immutable audit ledger of financial balance changes (activated in Phase B)
-- ---------------------------------------------------------------------
CREATE TABLE payment_posting_ledger (
    id                  BIGSERIAL PRIMARY KEY,
    voucher_id          BIGINT        NOT NULL,
    claim_id            BIGINT        NOT NULL,
    previous_paid       NUMERIC(12,2) NOT NULL,
    posted_paid         NUMERIC(12,2) NOT NULL,
    new_paid            NUMERIC(12,2) NOT NULL,
    previous_allowed    NUMERIC(12,2) NOT NULL,
    posted_allowed      NUMERIC(12,2) NOT NULL,
    new_allowed         NUMERIC(12,2) NOT NULL,
    posted_patient_resp NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    from_claim_status   VARCHAR(20)   NOT NULL,
    to_claim_status     VARCHAR(20)   NOT NULL,
    entry_type          VARCHAR(20)   NOT NULL DEFAULT 'ORIGINAL',
    posted_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    posted_by           VARCHAR(60)   NOT NULL,

    CONSTRAINT fk_ledger_voucher
        FOREIGN KEY (voucher_id) REFERENCES remittance_claim_voucher (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ledger_claim
        FOREIGN KEY (claim_id) REFERENCES claim (id) ON DELETE RESTRICT,
    CONSTRAINT ck_ledger_entry_type
        CHECK (entry_type IN ('ORIGINAL', 'REVERSAL', 'CORRECTION'))
);

CREATE INDEX ix_payment_ledger_claim ON payment_posting_ledger (claim_id);
CREATE INDEX ix_payment_ledger_date  ON payment_posting_ledger (posted_at);

