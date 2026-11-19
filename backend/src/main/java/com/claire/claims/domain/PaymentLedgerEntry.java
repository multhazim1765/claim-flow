package com.claire.claims.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "claim_payment_ledger")
public class PaymentLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id")
    private RemittanceClaimVoucher voucher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "remittance_id")
    private RemittanceBatch remittance;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 30)
    private PaymentLedgerEntryType entryType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "payer_icn", length = 50)
    private String payerIcn;

    @Column(name = "check_number", length = 50)
    private String checkNumber;

    @Column(name = "check_date")
    private LocalDate checkDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_ledger_id")
    private PaymentLedgerEntry originalLedgerEntry;

    @Column(length = 500)
    private String notes;

    @Column(name = "posted_by", nullable = false, length = 60)
    private String postedBy;

    @Column(name = "posted_at", nullable = false)
    private OffsetDateTime postedAt = OffsetDateTime.now();

    public PaymentLedgerEntry() {
    }

    public PaymentLedgerEntry(Claim claim,
                              RemittanceClaimVoucher voucher,
                              RemittanceBatch remittance,
                              PaymentLedgerEntryType entryType,
                              BigDecimal amount,
                              String payerIcn,
                              String checkNumber,
                              LocalDate checkDate,
                              String notes,
                              String postedBy) {
        this.claim = claim;
        this.voucher = voucher;
        this.remittance = remittance;
        this.entryType = entryType;
        this.amount = amount;
        this.payerIcn = payerIcn;
        this.checkNumber = checkNumber;
        this.checkDate = checkDate;
        this.notes = notes;
        this.postedBy = postedBy;
        this.postedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Claim getClaim() { return claim; }
    public void setClaim(Claim claim) { this.claim = claim; }

    public RemittanceClaimVoucher getVoucher() { return voucher; }
    public void setVoucher(RemittanceClaimVoucher voucher) { this.voucher = voucher; }

    public RemittanceBatch getRemittance() { return remittance; }
    public void setRemittance(RemittanceBatch remittance) { this.remittance = remittance; }

    public PaymentLedgerEntryType getEntryType() { return entryType; }
    public void setEntryType(PaymentLedgerEntryType entryType) { this.entryType = entryType; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getPayerIcn() { return payerIcn; }
    public void setPayerIcn(String payerIcn) { this.payerIcn = payerIcn; }

    public String getCheckNumber() { return checkNumber; }
    public void setCheckNumber(String checkNumber) { this.checkNumber = checkNumber; }

    public LocalDate getCheckDate() { return checkDate; }
    public void setCheckDate(LocalDate checkDate) { this.checkDate = checkDate; }

    public PaymentLedgerEntry getOriginalLedgerEntry() { return originalLedgerEntry; }
    public void setOriginalLedgerEntry(PaymentLedgerEntry originalLedgerEntry) { this.originalLedgerEntry = originalLedgerEntry; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getPostedBy() { return postedBy; }
    public void setPostedBy(String postedBy) { this.postedBy = postedBy; }

    public OffsetDateTime getPostedAt() { return postedAt; }
    public void setPostedAt(OffsetDateTime postedAt) { this.postedAt = postedAt; }
}

