package com.claire.claims.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "edi_export_batch_claim")
public class EdiExportBatchClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "export_batch_id", nullable = false)
    private EdiExportBatch batch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @Column(name = "claim_number_snapshot", nullable = false, length = 24)
    private String claimNumberSnapshot;

    @Column(name = "claim_charge_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal claimChargeSnapshot;

    @Column(name = "transaction_sequence", nullable = false)
    private int transactionSequence;

    @Column(name = "exported_at", nullable = false)
    private OffsetDateTime exportedAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public EdiExportBatch getBatch() { return batch; }
    public void setBatch(EdiExportBatch batch) { this.batch = batch; }

    public Claim getClaim() { return claim; }
    public void setClaim(Claim claim) { this.claim = claim; }

    public String getClaimNumberSnapshot() { return claimNumberSnapshot; }
    public void setClaimNumberSnapshot(String claimNumberSnapshot) { this.claimNumberSnapshot = claimNumberSnapshot; }

    public BigDecimal getClaimChargeSnapshot() { return claimChargeSnapshot; }
    public void setClaimChargeSnapshot(BigDecimal claimChargeSnapshot) { this.claimChargeSnapshot = claimChargeSnapshot; }

    public int getTransactionSequence() { return transactionSequence; }
    public void setTransactionSequence(int transactionSequence) { this.transactionSequence = transactionSequence; }

    public OffsetDateTime getExportedAt() { return exportedAt; }
    public void setExportedAt(OffsetDateTime exportedAt) { this.exportedAt = exportedAt; }
}

