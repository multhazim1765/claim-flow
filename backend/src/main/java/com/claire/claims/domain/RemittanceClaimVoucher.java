package com.claire.claims.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "remittance_claim_voucher")
public class RemittanceClaimVoucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "remittance_id", nullable = false)
    private RemittanceBatch remittance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id")
    private Claim claim;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Column(name = "claim_number", nullable = false, length = 24)
    private String claimNumber;

    @Column(name = "payer_icn", length = 50)
    private String payerIcn;

    @Column(name = "clp_status_code", nullable = false, length = 5)
    private String clpStatusCode;

    @Column(name = "billed_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal billedAmount = BigDecimal.ZERO;

    @Column(name = "allowed_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal allowedAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "patient_responsibility", nullable = false, precision = 12, scale = 2)
    private BigDecimal patientResponsibility = BigDecimal.ZERO;

    @Column(name = "contractual_adj", nullable = false, precision = 12, scale = 2)
    private BigDecimal contractualAdj = BigDecimal.ZERO;

    @Column(name = "other_adj", nullable = false, precision = 12, scale = 2)
    private BigDecimal otherAdj = BigDecimal.ZERO;

    @Column(name = "entry_type", nullable = false, length = 20)
    private String entryType = "ORIGINAL";

    @Column(name = "balance_status", nullable = false, length = 20)
    private String balanceStatus = "BALANCED";

    @Column(name = "posting_status", nullable = false, length = 20)
    private String postingStatus = "UNPOSTED";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_voucher_id")
    private RemittanceClaimVoucher originalVoucher;

    @Column(name = "posted_at")
    private OffsetDateTime postedAt;

    @Column(name = "posted_by", length = 60)
    private String postedBy;

    @OneToMany(mappedBy = "voucher", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RemittanceAdjustment> adjustments = new ArrayList<>();

    // ----- Helpers -----

    public void addAdjustment(RemittanceAdjustment adj) {
        adj.setVoucher(this);
        this.adjustments.add(adj);
    }

    // ----- Getters and Setters -----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public RemittanceBatch getRemittance() { return remittance; }
    public void setRemittance(RemittanceBatch remittance) { this.remittance = remittance; }

    public Claim getClaim() { return claim; }
    public void setClaim(Claim claim) { this.claim = claim; }

    public Integer getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(Integer sequenceNo) { this.sequenceNo = sequenceNo; }

    public String getClaimNumber() { return claimNumber; }
    public void setClaimNumber(String claimNumber) { this.claimNumber = claimNumber; }

    public String getPayerIcn() { return payerIcn; }
    public void setPayerIcn(String payerIcn) { this.payerIcn = payerIcn; }

    public String getClpStatusCode() { return clpStatusCode; }
    public void setClpStatusCode(String clpStatusCode) { this.clpStatusCode = clpStatusCode; }

    public BigDecimal getBilledAmount() { return billedAmount; }
    public void setBilledAmount(BigDecimal billedAmount) { this.billedAmount = billedAmount; }

    public BigDecimal getAllowedAmount() { return allowedAmount; }
    public void setAllowedAmount(BigDecimal allowedAmount) { this.allowedAmount = allowedAmount; }

    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }

    public BigDecimal getPatientResponsibility() { return patientResponsibility; }
    public void setPatientResponsibility(BigDecimal patientResponsibility) { this.patientResponsibility = patientResponsibility; }

    public BigDecimal getContractualAdj() { return contractualAdj; }
    public void setContractualAdj(BigDecimal contractualAdj) { this.contractualAdj = contractualAdj; }

    public BigDecimal getOtherAdj() { return otherAdj; }
    public void setOtherAdj(BigDecimal otherAdj) { this.otherAdj = otherAdj; }

    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }

    public String getBalanceStatus() { return balanceStatus; }
    public void setBalanceStatus(String balanceStatus) { this.balanceStatus = balanceStatus; }

    public String getPostingStatus() { return postingStatus; }
    public void setPostingStatus(String postingStatus) { this.postingStatus = postingStatus; }

    public RemittanceClaimVoucher getOriginalVoucher() { return originalVoucher; }
    public void setOriginalVoucher(RemittanceClaimVoucher originalVoucher) { this.originalVoucher = originalVoucher; }

    public OffsetDateTime getPostedAt() { return postedAt; }
    public void setPostedAt(OffsetDateTime postedAt) { this.postedAt = postedAt; }

    public String getPostedBy() { return postedBy; }
    public void setPostedBy(String postedBy) { this.postedBy = postedBy; }

    public List<RemittanceAdjustment> getAdjustments() { return adjustments; }
    public void setAdjustments(List<RemittanceAdjustment> adjustments) { this.adjustments = adjustments; }
}

