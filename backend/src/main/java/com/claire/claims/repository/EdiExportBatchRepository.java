package com.claire.claims.repository;

import com.claire.claims.domain.EdiExportBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface EdiExportBatchRepository extends JpaRepository<EdiExportBatch, Long> {

    Optional<EdiExportBatch> findByBatchNumber(String batchNumber);

    @Query("SELECT b FROM EdiExportBatch b WHERE (:payerId IS NULL OR b.payer.id = :payerId) AND (:status IS NULL OR b.exportStatus = :status)")
    Page<EdiExportBatch> findFiltered(@Param("payerId") Long payerId, @Param("status") String status, Pageable pageable);

    @Query("SELECT COUNT(b) FROM EdiExportBatch b")
    long countTotalBatches();

    @Query("SELECT COALESCE(SUM(b.claimCount), 0) FROM EdiExportBatch b")
    long sumExportedClaimsCount();

    @Query("SELECT COALESCE(SUM(b.totalBilledAmount), 0) FROM EdiExportBatch b")
    BigDecimal sumTotalBilledAmount();
}

