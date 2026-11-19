package com.claire.claims.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Persisted record of an adjudicated claim denial.
 * Captures CARC/RARC reason codes, urgency, deadline, and financial exposure.
 */
@Entity
@Table(name = "claim_denial")
public class ClaimDenial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_id", nullable = false)
    private Payer payer;

    @Column(name = "denial_code", nullable = false, length = 10)
    private String denialCode;

    @Column(name = "remittance_code", length = 10)
    private String remittanceCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "denial_category", nullable = false, length = 30)
    private DenialCategory denialCategory;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "denial_date", nullable = false)
    private LocalDate denialDate;

    @Column(name = "appeal_deadline", nullable = false)
    private LocalDate appealDeadline;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DenialStatus status = DenialStatus.OPEN;

    @Column(name = "created_by", nullable = false, length = 60)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public ClaimDenial() {
    }

    public ClaimDenial(Claim claim,
                       Payer payer,
                       String denialCode,
                       String remittanceCode,
                       DenialCategory denialCategory,
                       String reason,
                       LocalDate denialDate,
                       LocalDate appealDeadline,
                       BigDecimal amount,
                       String createdBy) {
        this.claim = claim;
        this.payer = payer;
        this.denialCode = denialCode;
        this.remittanceCode = remittanceCode;
        this.denialCategory = denialCategory;
        this.reason = reason;
        this.denialDate = denialDate;
        this.appealDeadline = appealDeadline;
        this.amount = amount;
        this.status = DenialStatus.OPEN;
        this.createdBy = createdBy;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Claim getClaim() {
        return claim;
    }

    public void setClaim(Claim claim) {
        this.claim = claim;
    }

    public Payer getPayer() {
        return payer;
    }

    public void setPayer(Payer payer) {
        this.payer = payer;
    }

    public String getDenialCode() {
        return denialCode;
    }

    public void setDenialCode(String denialCode) {
        this.denialCode = denialCode;
    }

    public String getRemittanceCode() {
        return remittanceCode;
    }

    public void setRemittanceCode(String remittanceCode) {
        this.remittanceCode = remittanceCode;
    }

    public DenialCategory getDenialCategory() {
        return denialCategory;
    }

    public void setDenialCategory(DenialCategory denialCategory) {
        this.denialCategory = denialCategory;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDate getDenialDate() {
        return denialDate;
    }

    public void setDenialDate(LocalDate denialDate) {
        this.denialDate = denialDate;
    }

    public LocalDate getAppealDeadline() {
        return appealDeadline;
    }

    public void setAppealDeadline(LocalDate appealDeadline) {
        this.appealDeadline = appealDeadline;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public DenialStatus getStatus() {
        return status;
    }

    public void setStatus(DenialStatus status) {
        this.status = status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

