package com.claire.claims.repository;

import com.claire.claims.domain.PaymentLedgerEntry;
import com.claire.claims.domain.PaymentLedgerEntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PaymentLedgerRepository extends JpaRepository<PaymentLedgerEntry, Long> {

    List<PaymentLedgerEntry> findByClaimIdOrderByPostedAtAsc(Long claimId);

    List<PaymentLedgerEntry> findByClaimIdOrderByIdAsc(Long claimId);

    boolean existsByVoucherIdAndEntryType(Long voucherId, PaymentLedgerEntryType entryType);

    boolean existsByOriginalLedgerEntryIdAndEntryType(Long originalId, PaymentLedgerEntryType entryType);

    List<PaymentLedgerEntry> findByVoucherId(Long voucherId);

    Optional<PaymentLedgerEntry> findByVoucherIdAndEntryType(Long voucherId, PaymentLedgerEntryType entryType);

    Optional<PaymentLedgerEntry> findByOriginalLedgerEntryIdAndEntryType(Long originalId, PaymentLedgerEntryType entryType);

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM PaymentLedgerEntry l WHERE l.claim.id = :claimId AND l.entryType IN ('LEGACY_INITIAL_BALANCE', 'PAYER_PAYMENT', 'PAYER_REVERSAL', 'PATIENT_PAYMENT')")
    BigDecimal sumNetPaymentsByClaimId(@Param("claimId") Long claimId);

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM PaymentLedgerEntry l WHERE l.claim.id = :claimId AND l.entryType IN ('CONTRACTUAL_WRITE_OFF', 'CONTRACTUAL_ADJUSTMENT_REVERSAL')")
    BigDecimal sumNetContractualAdjustmentByClaimId(@Param("claimId") Long claimId);

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM PaymentLedgerEntry l WHERE l.entryType = :entryType")
    BigDecimal sumAmountByEntryType(@Param("entryType") PaymentLedgerEntryType entryType);
}

