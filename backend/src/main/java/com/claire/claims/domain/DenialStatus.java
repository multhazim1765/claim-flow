package com.claire.claims.domain;

/**
 * Lifecycle status of a recorded denial.
 */
public enum DenialStatus {
    OPEN("Awaiting biller review or appeal filing"),
    APPEALED("Under appeal with payer"),
    RESOLVED("Overturned, corrected, or paid"),
    WRITTEN_OFF("Uncollectible or timely deadline expired without appeal");

    private final String description;

    DenialStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

