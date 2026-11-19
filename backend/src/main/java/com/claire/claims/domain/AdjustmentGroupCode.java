package com.claire.claims.domain;

/**
 * ANSI X12 835 Claim Adjustment Group Codes (CAS01).
 */
public enum AdjustmentGroupCode {
    CO("Contractual Obligations (provider contract write-offs)"),
    PR("Patient Responsibility (deductible, coinsurance, copay)"),
    OA("Other Adjustments (coordination of benefits, mandate reductions)"),
    PI("Payer Initiated Reductions");

    private final String description;

    AdjustmentGroupCode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

