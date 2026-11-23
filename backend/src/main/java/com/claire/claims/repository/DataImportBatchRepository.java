package com.claire.claims.repository;

import com.claire.claims.domain.DataImportBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataImportBatchRepository extends JpaRepository<DataImportBatch, Long> {
    Page<DataImportBatch> findByEntityTypeOrderByCreatedAtDesc(String entityType, Pageable pageable);
}

