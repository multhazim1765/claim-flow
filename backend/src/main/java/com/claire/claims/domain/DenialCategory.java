package com.claire.claims.domain;

/**
 * Standard denial categorization based on ANSI X12 835 / CARC root cause groups.
 */
public enum DenialCategory {
    AUTHORIZATION("Prior authorization or precertification absent or invalid"),
    MEDICAL_NECESSITY("Medical necessity not established or lacks clinical documentation"),
    TIMELY_FILING("Claim submitted past payer's timely filing deadline"),
    BILLING_CODING("Coding error, unbundling, missing modifier, or invalid procedure/diagnosis"),
    COVERAGE_ELIGIBILITY("Patient ineligible, coverage terminated, or service not covered"),
    DUPLICATE("Duplicate claim or service line previously processed"),
    OTHER("Other administrative or contractual payer denial");

    private final String description;

    DenialCategory(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

