-- =====================================================================
-- ClaimFlow Milestone 2.2: Denial Management & Multi-Level Appeals
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Table: claim_denial
-- Tracks structured CARC/RARC denial reasons, deadlines, and urgency
-- ---------------------------------------------------------------------
CREATE TABLE claim_denial (
    id               BIGSERIAL PRIMARY KEY,
    claim_id         BIGINT        NOT NULL,
    payer_id         BIGINT        NOT NULL,
    denial_code      VARCHAR(10)   NOT NULL, -- CARC code (e.g., CO-197, CO-16, CO-29)
    remittance_code  VARCHAR(10),            -- RARC code (e.g., N24, N130)
    denial_category  VARCHAR(30)   NOT NULL, -- AUTHORIZATION, MEDICAL_NECESSITY, TIMELY_FILING, BILLING_CODING, COVERAGE_ELIGIBILITY, DUPLICATE, OTHER
    reason           VARCHAR(500)  NOT NULL,
    denial_date      DATE          NOT NULL,
    appeal_deadline  DATE          NOT NULL,
    amount           NUMERIC(12,2) NOT NULL DEFAULT 0,
    status           VARCHAR(20)   NOT NULL DEFAULT 'OPEN', -- OPEN, APPEALED, RESOLVED, WRITTEN_OFF
    created_by       VARCHAR(60)   NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    
    CONSTRAINT fk_claim_denial_claim 
        FOREIGN KEY (claim_id) REFERENCES claim (id) ON DELETE RESTRICT,
    CONSTRAINT fk_claim_denial_payer 
        FOREIGN KEY (payer_id) REFERENCES payer (id) ON DELETE RESTRICT,
    CONSTRAINT ck_claim_denial_status 
        CHECK (status IN ('OPEN', 'APPEALED', 'RESOLVED', 'WRITTEN_OFF')),
    CONSTRAINT ck_claim_denial_category 
        CHECK (denial_category IN (
            'AUTHORIZATION', 'MEDICAL_NECESSITY', 'TIMELY_FILING', 
            'BILLING_CODING', 'COVERAGE_ELIGIBILITY', 'DUPLICATE', 'OTHER'
        )),
    CONSTRAINT ck_claim_denial_amount 
        CHECK (amount >= 0),
    CONSTRAINT ck_claim_denial_deadline 
        CHECK (appeal_deadline >= denial_date)
);

CREATE INDEX ix_claim_denial_claim    ON claim_denial (claim_id);
CREATE INDEX ix_claim_denial_payer    ON claim_denial (payer_id);
CREATE INDEX ix_claim_denial_deadline ON claim_denial (appeal_deadline);
CREATE INDEX ix_claim_denial_status   ON claim_denial (status);

-- ---------------------------------------------------------------------
-- 2. Table: claim_appeal
-- Multi-level appeal lifecycle (Reconsideration -> Formal -> External)
-- ---------------------------------------------------------------------
CREATE TABLE claim_appeal (
    id               BIGSERIAL PRIMARY KEY,
    claim_id         BIGINT        NOT NULL,
    denial_id        BIGINT,
    level            INTEGER       NOT NULL DEFAULT 1, -- 1: Reconsideration, 2: Formal Appeal, 3: External Review
    filed_on         DATE          NOT NULL,           -- Distinct from claim.submitted_at
    deadline         DATE          NOT NULL,           -- Response deadline for payer
    narrative        TEXT          NOT NULL,
    outcome          VARCHAR(20),                      -- NULL (Pending), UPHELD, OVERTURNED, PARTIAL, WITHDRAWN
    decided_on       DATE,
    recovered_amount NUMERIC(12,2) DEFAULT 0.00,       -- Payer accepted liability (reconciliation happens on remittance)
    decision_notes   VARCHAR(1000),
    filed_by         VARCHAR(60)   NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_claim_appeal_claim 
        FOREIGN KEY (claim_id) REFERENCES claim (id) ON DELETE RESTRICT,
    CONSTRAINT fk_claim_appeal_denial 
        FOREIGN KEY (denial_id) REFERENCES claim_denial (id) ON DELETE RESTRICT,
    CONSTRAINT ck_claim_appeal_level 
        CHECK (level BETWEEN 1 AND 3),
    CONSTRAINT ck_claim_appeal_outcome 
        CHECK (outcome IS NULL OR outcome IN ('UPHELD', 'OVERTURNED', 'PARTIAL', 'WITHDRAWN')),
    CONSTRAINT ck_claim_appeal_recovered 
        CHECK (recovered_amount >= 0)
);

CREATE INDEX ix_claim_appeal_claim   ON claim_appeal (claim_id);
CREATE INDEX ix_claim_appeal_denial  ON claim_appeal (denial_id);
CREATE INDEX ix_claim_appeal_outcome ON claim_appeal (outcome);

-- ---------------------------------------------------------------------
-- 3. Deterministic Seed Data (Safe for Existing Databases)
-- Resolves claim dynamically via business key 'CLM-2026-000004'
-- ---------------------------------------------------------------------
INSERT INTO claim_denial (
    claim_id,
    payer_id,
    denial_code,
    remittance_code,
    denial_category,
    reason,
    denial_date,
    appeal_deadline,
    amount,
    status,
    created_by,
    created_at
)
SELECT 
    c.id,
    c.payer_id,
    'CO-197',
    'N24',
    'AUTHORIZATION',
    'Precertification/authorization/notification/pre-treatment absent',
    DATE '2026-08-01',
    DATE '2026-10-30',
    c.total_charge,
    'OPEN',
    'biller',
    TIMESTAMPTZ '2026-08-01 14:30:00Z'
FROM claim c
WHERE c.claim_number = 'CLM-2026-000004'
  AND NOT EXISTS (
      SELECT 1 FROM claim_denial cd WHERE cd.claim_id = c.id
  );

