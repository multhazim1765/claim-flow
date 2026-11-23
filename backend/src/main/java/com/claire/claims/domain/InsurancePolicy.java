package com.claire.claims.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "insurance_policy")
public class InsurancePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_id", nullable = false)
    private Payer payer;

    @Column(name = "member_id", nullable = false, length = 50)
    private String memberId;

    @Column(name = "group_number", length = 50)
    private String groupNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PolicyPriority priority;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "termination_date")
    private LocalDate terminationDate;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "demo_copay", precision = 12, scale = 2)
    private BigDecimal demoCopay;

    @Column(name = "demo_coinsurance_percent")
    private Integer demoCoinsurancePercent;

    @Column(name = "demo_deductible_total", precision = 12, scale = 2)
    private BigDecimal demoDeductibleTotal;

    @Column(name = "demo_deductible_remaining", precision = 12, scale = 2)
    private BigDecimal demoDeductibleRemaining;

    @Column(name = "demo_out_of_pocket_remaining", precision = 12, scale = 2)
    private BigDecimal demoOutOfPocketRemaining;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = OffsetDateTime.now(); }

    /** True when this coverage is in force on the given date of service. */
    public boolean isActiveOn(LocalDate date) {
        if (!active) return false;
        if (date == null) return false;
        if (date.isBefore(effectiveDate)) return false;
        return terminationDate == null || !date.isAfter(terminationDate);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Patient getPatient() { return patient; }
    public void setPatient(Patient patient) { this.patient = patient; }
    public Payer getPayer() { return payer; }
    public void setPayer(Payer payer) { this.payer = payer; }
    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }
    public String getGroupNumber() { return groupNumber; }
    public void setGroupNumber(String groupNumber) { this.groupNumber = groupNumber; }
    public PolicyPriority getPriority() { return priority; }
    public void setPriority(PolicyPriority priority) { this.priority = priority; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }
    public LocalDate getTerminationDate() { return terminationDate; }
    public void setTerminationDate(LocalDate terminationDate) { this.terminationDate = terminationDate; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean hasCompleteDemoBenefitProfile() {
        return demoCopay != null
                && demoCoinsurancePercent != null
                && demoDeductibleTotal != null
                && demoDeductibleRemaining != null
                && demoOutOfPocketRemaining != null;
    }

    public boolean hasPartialDemoBenefitProfile() {
        int count = 0;
        if (demoCopay != null) count++;
        if (demoCoinsurancePercent != null) count++;
        if (demoDeductibleTotal != null) count++;
        if (demoDeductibleRemaining != null) count++;
        if (demoOutOfPocketRemaining != null) count++;
        return count > 0 && count < 5;
    }

    public BigDecimal getDemoCopay() { return demoCopay; }
    public void setDemoCopay(BigDecimal demoCopay) { this.demoCopay = demoCopay; }
    public Integer getDemoCoinsurancePercent() { return demoCoinsurancePercent; }
    public void setDemoCoinsurancePercent(Integer demoCoinsurancePercent) { this.demoCoinsurancePercent = demoCoinsurancePercent; }
    public BigDecimal getDemoDeductibleTotal() { return demoDeductibleTotal; }
    public void setDemoDeductibleTotal(BigDecimal demoDeductibleTotal) { this.demoDeductibleTotal = demoDeductibleTotal; }
    public BigDecimal getDemoDeductibleRemaining() { return demoDeductibleRemaining; }
    public void setDemoDeductibleRemaining(BigDecimal demoDeductibleRemaining) { this.demoDeductibleRemaining = demoDeductibleRemaining; }
    public BigDecimal getDemoOutOfPocketRemaining() { return demoOutOfPocketRemaining; }
    public void setDemoOutOfPocketRemaining(BigDecimal demoOutOfPocketRemaining) { this.demoOutOfPocketRemaining = demoOutOfPocketRemaining; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
