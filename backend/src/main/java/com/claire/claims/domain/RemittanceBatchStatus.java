package com.claire.claims.domain;

/**
 * Processing and reconciliation status of an imported ERA 835 remittance batch.
 */
public enum RemittanceBatchStatus {
    VALIDATED("Remittance batch parsed, balanced, and staged for review"),
    OUT_OF_BALANCE("Batch has arithmetic discrepancies or payment sum mismatch"),
    PARTIALLY_POSTED("Some claims have been posted; unresolved exceptions remain"),
    POSTED("All valid claim vouchers have been posted to claim balances"),
    REVERSED("Batch or vouchers have been reversed by payer notification");

    private final String description;

    RemittanceBatchStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

