package com.claire.claims.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "remittance_adjustment")
public class RemittanceAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voucher_id", nullable = false)
    private RemittanceClaimVoucher voucher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id")
    private Claim claim;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_line_id")
    private ClaimLine claimLine;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_code", nullable = false, length = 2)
    private AdjustmentGroupCode groupCode;

    @Column(name = "reason_code", nullable = false, length = 10)
    private String reasonCode;

    @Column(name = "remark_code", length = 10)
    private String remarkCode;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "entry_type", nullable = false, length = 20)
    private String entryType = "ORIGINAL";

    // ----- Getters and Setters -----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public RemittanceClaimVoucher getVoucher() { return voucher; }
    public void setVoucher(RemittanceClaimVoucher voucher) { this.voucher = voucher; }

    public Claim getClaim() { return claim; }
    public void setClaim(Claim claim) { this.claim = claim; }

    public ClaimLine getClaimLine() { return claimLine; }
    public void setClaimLine(ClaimLine claimLine) { this.claimLine = claimLine; }

    public AdjustmentGroupCode getGroupCode() { return groupCode; }
    public void setGroupCode(AdjustmentGroupCode groupCode) { this.groupCode = groupCode; }

    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }

    public String getRemarkCode() { return remarkCode; }
    public void setRemarkCode(String remarkCode) { this.remarkCode = remarkCode; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }
}

