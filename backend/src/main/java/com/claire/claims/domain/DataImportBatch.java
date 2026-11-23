package com.claire.claims.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "data_import_batch")
public class DataImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 30)
    private String entityType;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "imported_by", nullable = false, length = 60)
    private String importedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "error_summary", columnDefinition = "TEXT")
    private String errorSummary;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }
    public int getErrorCount() { return errorCount; }
    public void setErrorCount(int errorCount) { this.errorCount = errorCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getImportedBy() { return importedBy; }
    public void setImportedBy(String importedBy) { this.importedBy = importedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }
}

