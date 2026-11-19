package com.claire.claims.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "remittance_batch")
public class RemittanceBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_control_number", nullable = false, unique = true, length = 50)
    private String batchControlNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_id", nullable = false)
    private Payer payer;

    @Column(name = "payment_method", nullable = false, length = 10)
    private String paymentMethod = "ACH";

    @Column(name = "check_number", nullable = false, length = 50)
    private String checkNumber;

    @Column(name = "check_date", nullable = false)
    private LocalDate checkDate;

    @Column(name = "total_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPaid = BigDecimal.ZERO;

    @Column(name = "claims_count", nullable = false)
    private Integer claimsCount = 0;

    @Column(name = "matched_count", nullable = false)
    private Integer matchedCount = 0;

    @Column(name = "exception_count", nullable = false)
    private Integer exceptionCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RemittanceBatchStatus status = RemittanceBatchStatus.VALIDATED;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "imported_by", nullable = false, length = 60)
    private String importedBy;

    @Column(name = "imported_at", nullable = false)
    private OffsetDateTime importedAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "remittance", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNo ASC")
    private List<RemittanceClaimVoucher> vouchers = new ArrayList<>();

    @OneToMany(mappedBy = "remittance", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RemittanceClaimException> exceptions = new ArrayList<>();

    // ----- Helpers -----

    public void addVoucher(RemittanceClaimVoucher voucher) {
        voucher.setRemittance(this);
        this.vouchers.add(voucher);
    }

    public void addException(RemittanceClaimException exc) {
        exc.setRemittance(this);
        this.exceptions.add(exc);
    }

    // ----- Getters and Setters -----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBatchControlNumber() { return batchControlNumber; }
    public void setBatchControlNumber(String batchControlNumber) { this.batchControlNumber = batchControlNumber; }

    public Payer getPayer() { return payer; }
    public void setPayer(Payer payer) { this.payer = payer; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getCheckNumber() { return checkNumber; }
    public void setCheckNumber(String checkNumber) { this.checkNumber = checkNumber; }

    public LocalDate getCheckDate() { return checkDate; }
    public void setCheckDate(LocalDate checkDate) { this.checkDate = checkDate; }

    public BigDecimal getTotalPaid() { return totalPaid; }
    public void setTotalPaid(BigDecimal totalPaid) { this.totalPaid = totalPaid; }

    public Integer getClaimsCount() { return claimsCount; }
    public void setClaimsCount(Integer claimsCount) { this.claimsCount = claimsCount; }

    public Integer getMatchedCount() { return matchedCount; }
    public void setMatchedCount(Integer matchedCount) { this.matchedCount = matchedCount; }

    public Integer getExceptionCount() { return exceptionCount; }
    public void setExceptionCount(Integer exceptionCount) { this.exceptionCount = exceptionCount; }

    public RemittanceBatchStatus getStatus() { return status; }
    public void setStatus(RemittanceBatchStatus status) { this.status = status; }

    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }

    public String getImportedBy() { return importedBy; }
    public void setImportedBy(String importedBy) { this.importedBy = importedBy; }

    public OffsetDateTime getImportedAt() { return importedAt; }
    public void setImportedAt(OffsetDateTime importedAt) { this.importedAt = importedAt; }

    public List<RemittanceClaimVoucher> getVouchers() { return vouchers; }
    public void setVouchers(List<RemittanceClaimVoucher> vouchers) { this.vouchers = vouchers; }

    public List<RemittanceClaimException> getExceptions() { return exceptions; }
    public void setExceptions(List<RemittanceClaimException> exceptions) { this.exceptions = exceptions; }
}

