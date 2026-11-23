package com.claire.claims.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class BulkImportDtos {

    private BulkImportDtos() { }

    public record CsvRowValidation(
            int rowIndex,
            Map<String, String> data,
            List<String> errors,
            boolean valid,
            boolean duplicate) { }

    public record CsvPreviewResponse(
            String entityType,
            int totalRows,
            int validRows,
            int errorRows,
            int duplicateRows,
            List<String> headers,
            List<CsvRowValidation> rows) { }

    public record ExecuteImportRequest(
            String filename,
            List<Map<String, String>> rows,
            boolean abortOnError) { }

    public record CsvImportResultResponse(
            Long batchId,
            String entityType,
            int totalRows,
            int successCount,
            int errorCount,
            String status,
            List<String> errorMessages) { }

    public record ImportBatchSummaryResponse(
            Long id,
            String entityType,
            String filename,
            int totalRows,
            int successCount,
            int errorCount,
            String status,
            String importedBy,
            OffsetDateTime createdAt,
            String errorSummary) { }
}

