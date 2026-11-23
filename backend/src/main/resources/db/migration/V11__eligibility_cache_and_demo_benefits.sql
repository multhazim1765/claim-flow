-- =====================================================================
-- Migration V11: Enhanced Eligibility Cache Identity & Demo Policy Benefits
-- =====================================================================

-- Optional demonstration benefit overrides on insurance policies
ALTER TABLE insurance_policy
    ADD COLUMN IF NOT EXISTS demo_copay NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS demo_coinsurance_percent INTEGER,
    ADD COLUMN IF NOT EXISTS demo_deductible_total NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS demo_deductible_remaining NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS demo_out_of_pocket_remaining NUMERIC(12,2);

-- Validation constraints for demo policy benefits
ALTER TABLE insurance_policy
    ADD CONSTRAINT ck_policy_demo_copay
        CHECK (demo_copay IS NULL OR demo_copay >= 0),
    ADD CONSTRAINT ck_policy_demo_coins
        CHECK (demo_coinsurance_percent IS NULL OR (demo_coinsurance_percent >= 0 AND demo_coinsurance_percent <= 100)),
    ADD CONSTRAINT ck_policy_demo_ded_total
        CHECK (demo_deductible_total IS NULL OR demo_deductible_total >= 0),
    ADD CONSTRAINT ck_policy_demo_ded_rem
        CHECK (demo_deductible_remaining IS NULL OR demo_deductible_remaining >= 0),
    ADD CONSTRAINT ck_policy_demo_oop_rem
        CHECK (demo_out_of_pocket_remaining IS NULL OR demo_out_of_pocket_remaining >= 0),
    ADD CONSTRAINT ck_policy_demo_ded_rem_le_total
        CHECK ((demo_deductible_remaining IS NULL OR demo_deductible_total IS NULL) OR demo_deductible_remaining <= demo_deductible_total);

-- Eligibility check audit enhancement: benefit source and multi-dimensional cache fingerprint
ALTER TABLE eligibility_check
    ADD COLUMN IF NOT EXISTS benefit_source VARCHAR(50) NOT NULL DEFAULT 'SIMULATED_DEFAULT',
    ADD COLUMN IF NOT EXISTS cache_fingerprint VARCHAR(64);

ALTER TABLE eligibility_check
    ADD CONSTRAINT ck_eligibility_benefit_source
        CHECK (benefit_source IN ('SIMULATED_DEFAULT', 'SIMULATED_CUSTOM_DEMO_PROFILE'));

-- High-performance index for multi-dimensional cache matching
CREATE INDEX IF NOT EXISTS ix_eligibility_cache_v2
    ON eligibility_check (patient_id, payer_id, policy_id, service_date, service_type_code, cache_fingerprint, checked_at DESC);

