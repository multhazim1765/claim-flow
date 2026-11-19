package com.claire.claims.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Persisted record of a real-time 270/271 eligibility verification check.
 * Serves as clinical/billing evidence of coverage at time of service or submission.
 */
@Entity
@Table(name = "eligibility_check")
public class EligibilityCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_id", nullable = false)
    private Payer payer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id")
    private InsurancePolicy policy;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "service_type_code", nullable = false, length = 10)
    private String serviceTypeCode = "30";

    @Column(nullable = false)
    private boolean active;

    @Column(name = "checked_at", nullable = false)
    private OffsetDateTime checkedAt = OffsetDateTime.now();

    @Column(name = "plan_begin")
    private LocalDate planBegin;

    @Column(name = "plan_end")
    private LocalDate planEnd;

    @Column(precision = 12, scale = 2)
    private BigDecimal copay;

    @Column(name = "coinsurance_percent")
    private Integer coinsurancePercent;

    @Column(name = "deductible_total", precision = 12, scale = 2)
    private BigDecimal deductibleTotal;

    @Column(name = "deductible_remaining", precision = 12, scale = 2)
    private BigDecimal deductibleRemaining;

    @Column(name = "out_of_pocket_remaining", precision = 12, scale = 2)
    private BigDecimal outOfPocketRemaining;

    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    @Column(name = "checked_by", nullable = false, length = 60)
    private String checkedBy;

    @Column(nullable = false)
    private boolean cached = false;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public EligibilityCheck() { }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Patient getPatient() { return patient; }
    public void setPatient(Patient patient) { this.patient = patient; }

    public Payer getPayer() { return payer; }
    public void setPayer(Payer payer) { this.payer = payer; }

    public InsurancePolicy getPolicy() { return policy; }
    public void setPolicy(InsurancePolicy policy) { this.policy = policy; }

    public LocalDate getServiceDate() { return serviceDate; }
    public void setServiceDate(LocalDate serviceDate) { this.serviceDate = serviceDate; }

    public String getServiceTypeCode() { return serviceTypeCode; }
    public void setServiceTypeCode(String serviceTypeCode) { this.serviceTypeCode = serviceTypeCode; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public OffsetDateTime getCheckedAt() { return checkedAt; }
    public void setCheckedAt(OffsetDateTime checkedAt) { this.checkedAt = checkedAt; }

    public LocalDate getPlanBegin() { return planBegin; }
    public void setPlanBegin(LocalDate planBegin) { this.planBegin = planBegin; }

    public LocalDate getPlanEnd() { return planEnd; }
    public void setPlanEnd(LocalDate planEnd) { this.planEnd = planEnd; }

    public BigDecimal getCopay() { return copay; }
    public void setCopay(BigDecimal copay) { this.copay = copay; }

    public Integer getCoinsurancePercent() { return coinsurancePercent; }
    public void setCoinsurancePercent(Integer coinsurancePercent) { this.coinsurancePercent = coinsurancePercent; }

    public BigDecimal getDeductibleTotal() { return deductibleTotal; }
    public void setDeductibleTotal(BigDecimal deductibleTotal) { this.deductibleTotal = deductibleTotal; }

    public BigDecimal getDeductibleRemaining() { return deductibleRemaining; }
    public void setDeductibleRemaining(BigDecimal deductibleRemaining) { this.deductibleRemaining = deductibleRemaining; }

    public BigDecimal getOutOfPocketRemaining() { return outOfPocketRemaining; }
    public void setOutOfPocketRemaining(BigDecimal outOfPocketRemaining) { this.outOfPocketRemaining = outOfPocketRemaining; }

    public String getRawResponse() { return rawResponse; }
    public void setRawResponse(String rawResponse) { this.rawResponse = rawResponse; }

    public String getCheckedBy() { return checkedBy; }
    public void setCheckedBy(String checkedBy) { this.checkedBy = checkedBy; }

    public boolean isCached() { return cached; }
    public void setCached(boolean cached) { this.cached = cached; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

