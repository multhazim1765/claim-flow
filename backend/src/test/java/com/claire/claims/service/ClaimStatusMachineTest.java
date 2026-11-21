package com.claire.claims.service;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.common.ApiExceptions.ConflictException;
import com.claire.claims.domain.Claim;
import com.claire.claims.domain.ClaimStatus;
import com.claire.claims.domain.ClaimStatusHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaimStatusMachineTest {

    private ClaimStatusMachine statusMachine;
    private Claim claim;

    @BeforeEach
    void setUp() {
        statusMachine = new ClaimStatusMachine();
        claim = new Claim();
        claim.setId(99L);
        claim.setClaimNumber("CLM-2026-TEST99");
    }

    @Test
    @DisplayName("Payment posting from SUBMITTED creates two discrete audit transitions: SUBMITTED -> ACCEPTED -> PARTIALLY_PAID")
    void testPaymentFromSubmittedToPartiallyPaid() {
        claim.setStatus(ClaimStatus.SUBMITTED);

        List<ClaimStatusHistory> transitions = statusMachine.applyPaymentPosting(
                claim,
                ClaimStatus.PARTIALLY_PAID,
                new BigDecimal("500.00"),
                "EFT-TEST-001",
                "test_biller"
        );

        assertThat(transitions).hasSize(2);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.PARTIALLY_PAID);

        // Step 1: Inferred adjudication
        ClaimStatusHistory t1 = transitions.get(0);
        assertThat(t1.getFromStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(t1.getToStatus()).isEqualTo(ClaimStatus.ACCEPTED);
        assertThat(t1.getReason()).contains("Payer adjudication inferred via ERA 835 remittance check EFT-TEST-001");
        assertThat(t1.getReason()).contains("inferred from ERA, not separate 277CA acknowledgment");
        assertThat(t1.getChangedBy()).isEqualTo("test_biller");

        // Step 2: Payment posting
        ClaimStatusHistory t2 = transitions.get(1);
        assertThat(t2.getFromStatus()).isEqualTo(ClaimStatus.ACCEPTED);
        assertThat(t2.getToStatus()).isEqualTo(ClaimStatus.PARTIALLY_PAID);
        assertThat(t2.getReason()).isEqualTo("Payment of $500.00 posted from ERA 835 check EFT-TEST-001");
        assertThat(t2.getChangedBy()).isEqualTo("test_biller");
    }

    @Test
    @DisplayName("Payment posting from SUBMITTED creates two discrete audit transitions: SUBMITTED -> ACCEPTED -> PAID")
    void testPaymentFromSubmittedToPaid() {
        claim.setStatus(ClaimStatus.SUBMITTED);

        List<ClaimStatusHistory> transitions = statusMachine.applyPaymentPosting(
                claim,
                ClaimStatus.PAID,
                new BigDecimal("750.00"),
                "EFT-TEST-002",
                "test_biller"
        );

        assertThat(transitions).hasSize(2);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.PAID);

        assertThat(transitions.get(0).getFromStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(transitions.get(0).getToStatus()).isEqualTo(ClaimStatus.ACCEPTED);

        assertThat(transitions.get(1).getFromStatus()).isEqualTo(ClaimStatus.ACCEPTED);
        assertThat(transitions.get(1).getToStatus()).isEqualTo(ClaimStatus.PAID);
    }

    @Test
    @DisplayName("Payment posting from ACCEPTED creates a single transition: ACCEPTED -> PAID")
    void testPaymentFromAcceptedToPaid() {
        claim.setStatus(ClaimStatus.ACCEPTED);

        List<ClaimStatusHistory> transitions = statusMachine.applyPaymentPosting(
                claim,
                ClaimStatus.PAID,
                new BigDecimal("300.00"),
                "EFT-TEST-003",
                "test_biller"
        );

        assertThat(transitions).hasSize(1);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.PAID);
        assertThat(transitions.get(0).getFromStatus()).isEqualTo(ClaimStatus.ACCEPTED);
        assertThat(transitions.get(0).getToStatus()).isEqualTo(ClaimStatus.PAID);
    }

    @Test
    @DisplayName("Payment posting from PARTIALLY_PAID to PAID creates a single transition")
    void testPaymentFromPartiallyPaidToPaid() {
        claim.setStatus(ClaimStatus.PARTIALLY_PAID);

        List<ClaimStatusHistory> transitions = statusMachine.applyPaymentPosting(
                claim,
                ClaimStatus.PAID,
                new BigDecimal("150.00"),
                "EFT-TEST-004",
                "test_biller"
        );

        assertThat(transitions).hasSize(1);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.PAID);
        assertThat(transitions.get(0).getFromStatus()).isEqualTo(ClaimStatus.PARTIALLY_PAID);
        assertThat(transitions.get(0).getToStatus()).isEqualTo(ClaimStatus.PAID);
    }

    @Test
    @DisplayName("Payment posting to an already PARTIALLY_PAID claim that stays PARTIALLY_PAID creates zero transitions (no-op prevented)")
    void testPaymentFromPartiallyPaidToPartiallyPaidNoOp() {
        claim.setStatus(ClaimStatus.PARTIALLY_PAID);

        List<ClaimStatusHistory> transitions = statusMachine.applyPaymentPosting(
                claim,
                ClaimStatus.PARTIALLY_PAID,
                new BigDecimal("75.00"),
                "EFT-TEST-005",
                "test_biller"
        );

        assertThat(transitions).isEmpty();
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.PARTIALLY_PAID);
    }

    @Test
    @DisplayName("Payment posting to an already PAID claim throws ConflictException")
    void testPaymentToPaidClaimThrowsConflict() {
        claim.setStatus(ClaimStatus.PAID);

        assertThatThrownBy(() -> statusMachine.applyPaymentPosting(
                claim,
                ClaimStatus.PAID,
                new BigDecimal("50.00"),
                "EFT-TEST-006",
                "test_biller"
        )).isInstanceOf(ConflictException.class)
          .hasMessageContaining("already PAID");
    }

    @Test
    @DisplayName("Payment posting to incompatible statuses (DRAFT, VOID, REJECTED, DENIED) throws BusinessRuleException")
    void testPaymentToIncompatibleStatusesThrowsBusinessRule() {
        for (ClaimStatus badStatus : List.of(ClaimStatus.DRAFT, ClaimStatus.VOID, ClaimStatus.REJECTED, ClaimStatus.DENIED)) {
            claim.setStatus(badStatus);
            assertThatThrownBy(() -> statusMachine.applyPaymentPosting(
                    claim,
                    ClaimStatus.PARTIALLY_PAID,
                    new BigDecimal("50.00"),
                    "EFT-TEST-007",
                    "test_biller"
            )).isInstanceOf(BusinessRuleException.class)
              .hasMessageContaining("Cannot post payment to claim");
        }
    }

    @Test
    @DisplayName("Payment reversal on PAID claim reopening to ACCEPTED when remaining paid is zero")
    void testPaymentReversalFromPaidToAccepted() {
        claim.setStatus(ClaimStatus.PAID);

        List<ClaimStatusHistory> transitions = statusMachine.applyPaymentReversal(
                claim,
                BigDecimal.ZERO,
                "CHK-REV-001",
                "admin"
        );

        assertThat(transitions).hasSize(1);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.ACCEPTED);
        ClaimStatusHistory t = transitions.get(0);
        assertThat(t.getFromStatus()).isEqualTo(ClaimStatus.PAID);
        assertThat(t.getToStatus()).isEqualTo(ClaimStatus.ACCEPTED);
        assertThat(t.getReason()).contains("Payment reversal applied (check CHK-REV-001): remaining paid $0.00");
        assertThat(t.getChangedBy()).isEqualTo("admin");
    }

    @Test
    @DisplayName("Payment reversal on PAID claim reopening to PARTIALLY_PAID when remaining paid > 0")
    void testPaymentReversalFromPaidToPartiallyPaid() {
        claim.setStatus(ClaimStatus.PAID);

        List<ClaimStatusHistory> transitions = statusMachine.applyPaymentReversal(
                claim,
                new BigDecimal("250.00"),
                "CHK-REV-002",
                "admin"
        );

        assertThat(transitions).hasSize(1);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.PARTIALLY_PAID);
        ClaimStatusHistory t = transitions.get(0);
        assertThat(t.getFromStatus()).isEqualTo(ClaimStatus.PAID);
        assertThat(t.getToStatus()).isEqualTo(ClaimStatus.PARTIALLY_PAID);
        assertThat(t.getReason()).contains("remaining paid $250.00");
    }

    @Test
    @DisplayName("Payment reversal on PARTIALLY_PAID claim reopening to ACCEPTED when remaining paid is zero")
    void testPaymentReversalFromPartiallyPaidToAccepted() {
        claim.setStatus(ClaimStatus.PARTIALLY_PAID);

        List<ClaimStatusHistory> transitions = statusMachine.applyPaymentReversal(
                claim,
                BigDecimal.ZERO,
                "CHK-REV-003",
                "biller"
        );

        assertThat(transitions).hasSize(1);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.ACCEPTED);
        assertThat(transitions.get(0).getFromStatus()).isEqualTo(ClaimStatus.PARTIALLY_PAID);
        assertThat(transitions.get(0).getToStatus()).isEqualTo(ClaimStatus.ACCEPTED);
    }

    @Test
    @DisplayName("Payment reversal on PARTIALLY_PAID claim staying PARTIALLY_PAID produces no redundant transition row")
    void testPaymentReversalFromPartiallyPaidToPartiallyPaidNoOp() {
        claim.setStatus(ClaimStatus.PARTIALLY_PAID);

        List<ClaimStatusHistory> transitions = statusMachine.applyPaymentReversal(
                claim,
                new BigDecimal("100.00"),
                "CHK-REV-004",
                "biller"
        );

        assertThat(transitions).isEmpty();
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.PARTIALLY_PAID);
    }

    @Test
    @DisplayName("Payment reversal on invalid status (DRAFT, VOID, REJECTED, SUBMITTED) throws BusinessRuleException")
    void testPaymentReversalOnInvalidStatusThrows() {
        for (ClaimStatus badStatus : List.of(ClaimStatus.DRAFT, ClaimStatus.VOID, ClaimStatus.REJECTED, ClaimStatus.SUBMITTED)) {
            claim.setStatus(badStatus);
            assertThatThrownBy(() -> statusMachine.applyPaymentReversal(
                    claim,
                    BigDecimal.ZERO,
                    "CHK-REV-BAD",
                    "biller"
            )).isInstanceOf(BusinessRuleException.class)
              .hasMessageContaining("Cannot reverse payment on claim");
        }
    }

    @Test
    @DisplayName("Payment reversal with negative remaining paid throws BusinessRuleException")
    void testPaymentReversalWithNegativeRemainingThrows() {
        claim.setStatus(ClaimStatus.PAID);
        assertThatThrownBy(() -> statusMachine.applyPaymentReversal(
                claim,
                new BigDecimal("-50.00"),
                "CHK-REV-NEG",
                "biller"
        )).isInstanceOf(BusinessRuleException.class)
          .hasMessageContaining("negative paid amount");
    }
}

