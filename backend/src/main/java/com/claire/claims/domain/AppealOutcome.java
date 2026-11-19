package com.claire.claims.domain;

/**
 * Payer decision outcome for an adjudicated claim appeal.
 */
public enum AppealOutcome {
    UPHELD("Payer upheld the denial; denial maintained"),
    OVERTURNED("Payer overturned the denial in full; claim approved for payment processing"),
    PARTIAL("Payer partially overturned the denial; partial payment approved"),
    WITHDRAWN("Appeal withdrawn by the provider or billing entity");

    private final String description;

    AppealOutcome(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

