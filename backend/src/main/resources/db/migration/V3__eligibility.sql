-- =====================================================================
-- ClaimFlow: Real-Time Eligibility Verification (270/271)
-- =====================================================================

CREATE TABLE eligibility_check (
    id                      BIGSERIAL PRIMARY KEY,
    patient_id              BIGINT        NOT NULL REFERENCES patient (id),
    payer_id                BIGINT        NOT NULL REFERENCES payer (id),
    policy_id               BIGINT        REFERENCES insurance_policy (id),
    service_date            DATE          NOT NULL,
    service_type_code       VARCHAR(10)   NOT NULL DEFAULT '30',
    active                  BOOLEAN       NOT NULL,
    checked_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    plan_begin              DATE,
    plan_end                DATE,
    copay                   NUMERIC(12,2),
    coinsurance_percent     INTEGER,
    deductible_total        NUMERIC(12,2),
    deductible_remaining    NUMERIC(12,2),
    out_of_pocket_remaining NUMERIC(12,2),
    raw_response            TEXT,
    checked_by              VARCHAR(60)   NOT NULL,
    cached                  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_eligibility_cache 
    ON eligibility_check (patient_id, payer_id, service_date, checked_at DESC);

CREATE INDEX ix_eligibility_patient 
    ON eligibility_check (patient_id, checked_at DESC);

