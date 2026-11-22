package com.claire.claims.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "edi_export_batch")
public class EdiExportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_number", nullable = false, unique = true, length = 32)
    private String batchNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payer_id")
    private Payer payer;

    @Column(name = "interchange_control_number", nullable = false, length = 9)
    private String interchangeControlNumber;

    @Column(name = "group_control_number", nullable = false, length = 9)
    private String groupControlNumber;

    @Column(name = "transaction_control_number", nullable = false, length = 9)
    private String transactionControlNumber;

    @Column(name = "export_status", nullable = false, length = 20)
    private String exportStatus = "EXPORTED";

    @Column(name = "claim_count", nullable = false)
    private int claimCount;

    @Column(name = "total_billed_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalBilledAmount = BigDecimal.ZERO;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_by", nullable = false, length = 60)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "exported_at", nullable = false)
    private OffsetDateTime exportedAt = OffsetDateTime.now();

    @Column(name = "validation_summary", columnDefinition = "TEXT")
    private String validationSummary;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("transactionSequence ASC")
    private List<EdiExportBatchClaim> claims = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

    public Payer getPayer() { return payer; }
    public void setPayer(Payer payer) { this.payer = payer; }

    public String getInterchangeControlNumber() { return interchangeControlNumber; }
    public void setInterchangeControlNumber(String interchangeControlNumber) { this.interchangeControlNumber = interchangeControlNumber; }

    public String getGroupControlNumber() { return groupControlNumber; }
    public void setGroupControlNumber(String groupControlNumber) { this.groupControlNumber = groupControlNumber; }

    public String getTransactionControlNumber() { return transactionControlNumber; }
    public void setTransactionControlNumber(String transactionControlNumber) { this.transactionControlNumber = transactionControlNumber; }

    public String getExportStatus() { return exportStatus; }
    public void setExportStatus(String exportStatus) { this.exportStatus = exportStatus; }

    public int getClaimCount() { return claimCount; }
    public void setClaimCount(int claimCount) { this.claimCount = claimCount; }

    public BigDecimal getTotalBilledAmount() { return totalBilledAmount; }
    public void setTotalBilledAmount(BigDecimal totalBilledAmount) { this.totalBilledAmount = totalBilledAmount; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getExportedAt() { return exportedAt; }
    public void setExportedAt(OffsetDateTime exportedAt) { this.exportedAt = exportedAt; }

    public String getValidationSummary() { return validationSummary; }
    public void setValidationSummary(String validationSummary) { this.validationSummary = validationSummary; }

    public List<EdiExportBatchClaim> getClaims() { return claims; }
    public void setClaims(List<EdiExportBatchClaim> claims) { this.claims = claims; }
}
