package com.claire.claims.service;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.common.ApiExceptions.ConflictException;
import com.claire.claims.common.ApiExceptions.NotFoundException;
import com.claire.claims.domain.*;
import com.claire.claims.dto.AppealDtos.*;
import com.claire.claims.dto.ClaimDtos.TransitionRequest;
import com.claire.claims.repository.ClaimAppealRepository;
import com.claire.claims.repository.ClaimDenialRepository;
import com.claire.claims.repository.ClaimRepository;
import com.claire.claims.repository.ClaimStatusHistoryRepository;
import com.claire.claims.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppealServiceTest {

    @Mock
    private ClaimAppealRepository appeals;
    @Mock
    private ClaimRepository claims;
    @Mock
    private ClaimDenialRepository denials;
    @Mock
    private ClaimStatusHistoryRepository statusHistory;
    @Spy
    private ClaimStatusMachine statusMachine = new ClaimStatusMachine();
    @Mock
    private CurrentUserProvider currentUser;

    @InjectMocks
    private AppealService appealService;

    private Claim sampleClaim;
    private ClaimDenial sampleDenial;
    private OffsetDateTime originalSubmittedAt;

    @BeforeEach
    void setUp() {
        Payer payer = new Payer();
        payer.setId(2L);
        payer.setName("Aetna Health");

        Patient patient = new Patient();
        patient.setId(10L);
        patient.setFirstName("Robert");
        patient.setLastName("Martinez");
        patient.setMrn("MRN-100004");

        originalSubmittedAt = OffsetDateTime.now().minusDays(30);

        sampleClaim = new Claim();
        sampleClaim.setId(4L);
        sampleClaim.setClaimNumber("CLM-2026-000004");
        sampleClaim.setStatus(ClaimStatus.DENIED);
        sampleClaim.setPayer(payer);
        sampleClaim.setPatient(patient);
        sampleClaim.setTotalCharge(new BigDecimal("1450.00"));
        sampleClaim.setAllowedAmount(BigDecimal.ZERO);
        sampleClaim.setPaidAmount(BigDecimal.ZERO);
        sampleClaim.setSubmittedAt(originalSubmittedAt);

        sampleDenial = new ClaimDenial();
        sampleDenial.setId(1L);
        sampleDenial.setClaim(sampleClaim);
        sampleDenial.setPayer(payer);
        sampleDenial.setDenialCode("CO-197");
        sampleDenial.setDenialCategory(DenialCategory.AUTHORIZATION);
        sampleDenial.setReason("Precertification absent");
        sampleDenial.setDenialDate(LocalDate.now().minusDays(10));
        sampleDenial.setAppealDeadline(LocalDate.now().plusDays(20)); // valid future deadline
        sampleDenial.setAmount(new BigDecimal("1450.00"));
        sampleDenial.setStatus(DenialStatus.OPEN);
        sampleDenial.setCreatedBy("biller");
    }

    // =========================================================================
    // 1. Filing Appeals
    // =========================================================================

    @Test
    @DisplayName("Successfully files Level 1 appeal, updates denial, and preserves submittedAt")
    void testFileAppeal_Level1_Success() {
        when(claims.findWithReferencesById(4L)).thenReturn(Optional.of(sampleClaim));
        when(denials.findByClaimIdOrderByCreatedAtDesc(4L)).thenReturn(List.of(sampleDenial));
        when(appeals.existsByClaimIdAndOutcomeIsNull(4L)).thenReturn(false);
        when(appeals.findByClaimIdOrderByLevelAsc(4L)).thenReturn(new ArrayList<>());
        when(currentUser.username()).thenReturn("biller");
        when(appeals.save(any(ClaimAppeal.class))).thenAnswer(inv -> {
            ClaimAppeal a = inv.getArgument(0);
            a.setId(101L);
            return a;
        });

        FileAppealRequest req = new FileAppealRequest(null, "Retroactive precertification submitted with chart notes", 45);
        AppealResponse res = appealService.fileAppeal(4L, req);

        assertThat(res).isNotNull();
        assertThat(res.id()).isEqualTo(101L);
        assertThat(res.claimId()).isEqualTo(4L);
        assertThat(res.level()).isEqualTo(1);
        assertThat(res.levelDescription()).isEqualTo("Level 1: Reconsideration");
        assertThat(res.resultingClaimStatus()).isEqualTo(ClaimStatus.APPEALED);
        assertThat(sampleClaim.getStatus()).isEqualTo(ClaimStatus.APPEALED);
        assertThat(sampleDenial.getStatus()).isEqualTo(DenialStatus.APPEALED);

        // Rule 5: submittedAt must remain untouched
        assertThat(sampleClaim.getSubmittedAt()).isEqualTo(originalSubmittedAt);

        // Rule 10: status history recorded
        verify(statusHistory).save(any(ClaimStatusHistory.class));
        verify(denials).save(sampleDenial);
    }

    @Test
    @DisplayName("Fails filing appeal on non-DENIED claim")
    void testFileAppeal_NotDenied_ThrowsConflict() {
        sampleClaim.setStatus(ClaimStatus.ACCEPTED);
        when(claims.findWithReferencesById(4L)).thenReturn(Optional.of(sampleClaim));

        FileAppealRequest req = new FileAppealRequest(null, "Appeal narrative", 30);
        assertThatThrownBy(() -> appealService.fileAppeal(4L, req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Only DENIED claims can be appealed");
    }

    @Test
    @DisplayName("Fails filing appeal when denial appeal deadline has expired")
    void testFileAppeal_ExpiredDeadline_ThrowsBusinessRuleException() {
        sampleDenial.setAppealDeadline(LocalDate.now().minusDays(5)); // expired!
        when(claims.findWithReferencesById(4L)).thenReturn(Optional.of(sampleClaim));
        when(denials.findByClaimIdOrderByCreatedAtDesc(4L)).thenReturn(List.of(sampleDenial));

        FileAppealRequest req = new FileAppealRequest(null, "Appeal narrative", 30);
        assertThatThrownBy(() -> appealService.fileAppeal(4L, req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("appeal deadline")
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("Fails filing duplicate pending appeal")
    void testFileAppeal_DuplicatePending_ThrowsConflict() {
        when(claims.findWithReferencesById(4L)).thenReturn(Optional.of(sampleClaim));
        when(denials.findByClaimIdOrderByCreatedAtDesc(4L)).thenReturn(List.of(sampleDenial));
        when(appeals.existsByClaimIdAndOutcomeIsNull(4L)).thenReturn(true);

        FileAppealRequest req = new FileAppealRequest(null, "Second appeal attempt", 30);
        assertThatThrownBy(() -> appealService.fileAppeal(4L, req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already has a pending appeal awaiting decision");
    }

    @Test
    @DisplayName("Level progression: Level 2 succeeds after Level 1 is UPHELD")
    void testFileAppeal_Level2_SuccessAfterLevel1Upheld() {
        ClaimAppeal level1 = new ClaimAppeal(sampleClaim, sampleDenial, 1, LocalDate.now().minusDays(20), LocalDate.now().plusDays(10), "L1 narrative", "biller");
        level1.setOutcome(AppealOutcome.UPHELD);
        level1.setDecidedOn(LocalDate.now().minusDays(2));

        when(claims.findWithReferencesById(4L)).thenReturn(Optional.of(sampleClaim));
        when(denials.findByClaimIdOrderByCreatedAtDesc(4L)).thenReturn(List.of(sampleDenial));
        when(appeals.existsByClaimIdAndOutcomeIsNull(4L)).thenReturn(false);
        when(appeals.findByClaimIdOrderByLevelAsc(4L)).thenReturn(List.of(level1));
        when(currentUser.username()).thenReturn("biller");
        when(appeals.save(any(ClaimAppeal.class))).thenAnswer(inv -> {
            ClaimAppeal a = inv.getArgument(0);
            a.setId(102L);
            return a;
        });

        FileAppealRequest req = new FileAppealRequest(null, "Formal appeal with medical director letter", 60);
        AppealResponse res = appealService.fileAppeal(4L, req);

        assertThat(res.level()).isEqualTo(2);
        assertThat(res.levelDescription()).isEqualTo("Level 2: Formal Appeal");
    }

    @Test
    @DisplayName("Level progression: Cannot escalate if previous level was OVERTURNED")
    void testFileAppeal_CannotEscalateIfOverturned() {
        ClaimAppeal level1 = new ClaimAppeal(sampleClaim, sampleDenial, 1, LocalDate.now().minusDays(20), LocalDate.now().plusDays(10), "L1 narrative", "biller");
        level1.setOutcome(AppealOutcome.OVERTURNED);
        level1.setDecidedOn(LocalDate.now().minusDays(2));

        when(claims.findWithReferencesById(4L)).thenReturn(Optional.of(sampleClaim));
        when(denials.findByClaimIdOrderByCreatedAtDesc(4L)).thenReturn(List.of(sampleDenial));
        when(appeals.existsByClaimIdAndOutcomeIsNull(4L)).thenReturn(false);
        when(appeals.findByClaimIdOrderByLevelAsc(4L)).thenReturn(List.of(level1));

        FileAppealRequest req = new FileAppealRequest(null, "Escalation attempt", 60);
        assertThatThrownBy(() -> appealService.fileAppeal(4L, req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cannot escalate appeal: previous Level 1 outcome was OVERTURNED");
    }

    @Test
    @DisplayName("Maximum level: Cannot file appeal past Level 3")
    void testFileAppeal_CannotExceedLevel3() {
        ClaimAppeal l1 = new ClaimAppeal(sampleClaim, sampleDenial, 1, LocalDate.now().minusDays(40), LocalDate.now().minusDays(20), "L1", "biller");
        l1.setOutcome(AppealOutcome.UPHELD);
        ClaimAppeal l2 = new ClaimAppeal(sampleClaim, sampleDenial, 2, LocalDate.now().minusDays(20), LocalDate.now().minusDays(10), "L2", "biller");
        l2.setOutcome(AppealOutcome.UPHELD);
        ClaimAppeal l3 = new ClaimAppeal(sampleClaim, sampleDenial, 3, LocalDate.now().minusDays(10), LocalDate.now().minusDays(1), "L3", "biller");
        l3.setOutcome(AppealOutcome.UPHELD);

        when(claims.findWithReferencesById(4L)).thenReturn(Optional.of(sampleClaim));
        when(denials.findByClaimIdOrderByCreatedAtDesc(4L)).thenReturn(List.of(sampleDenial));
        when(appeals.existsByClaimIdAndOutcomeIsNull(4L)).thenReturn(false);
        when(appeals.findByClaimIdOrderByLevelAsc(4L)).thenReturn(List.of(l1, l2, l3));

        FileAppealRequest req = new FileAppealRequest(null, "Level 4 attempt", 60);
        assertThatThrownBy(() -> appealService.fileAppeal(4L, req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("maximum allowed appeal tier (Level 3: External Review)");
    }

    // =========================================================================
    // 2. Appeal Outcomes
    // =========================================================================

    @Test
    @DisplayName("OVERTURNED outcome moves claim to ACCEPTED and preserves zero paidAmount")
    void testRecordOutcome_Overturned_MovesToAccepted_NotPaid() {
        sampleClaim.setStatus(ClaimStatus.APPEALED);
        sampleDenial.setStatus(DenialStatus.APPEALED);

        ClaimAppeal appeal = new ClaimAppeal(sampleClaim, sampleDenial, 1, LocalDate.now().minusDays(15), LocalDate.now().plusDays(15), "L1", "biller");
        appeal.setId(201L);

        when(appeals.findWithReferencesById(201L)).thenReturn(Optional.of(appeal));
        when(currentUser.username()).thenReturn("biller");
        when(appeals.save(any(ClaimAppeal.class))).thenAnswer(inv -> inv.getArgument(0));

        RecordOutcomeRequest req = new RecordOutcomeRequest(
                AppealOutcome.OVERTURNED,
                LocalDate.now().minusDays(1),
                new BigDecimal("1450.00"),
                "Payer agreed authorization was valid"
        );

        AppealResponse res = appealService.recordOutcome(201L, req);

        // Rule 7 & 9: Claim transitions to ACCEPTED, NOT PAID
        assertThat(res.resultingClaimStatus()).isEqualTo(ClaimStatus.ACCEPTED);
        assertThat(sampleClaim.getStatus()).isEqualTo(ClaimStatus.ACCEPTED);
        assertThat(sampleClaim.getPaidAmount()).isEqualByComparingTo("0.00");
        assertThat(sampleClaim.getAllowedAmount()).isEqualByComparingTo("0.00");

        // Denial status becomes RESOLVED
        assertThat(sampleDenial.getStatus()).isEqualTo(DenialStatus.RESOLVED);

        // Audit entry created
        ArgumentCaptor<ClaimStatusHistory> historyCaptor = ArgumentCaptor.forClass(ClaimStatusHistory.class);
        verify(statusHistory).save(historyCaptor.capture());
        ClaimStatusHistory h = historyCaptor.getValue();
        assertThat(h.getFromStatus()).isEqualTo(ClaimStatus.APPEALED);
        assertThat(h.getToStatus()).isEqualTo(ClaimStatus.ACCEPTED);
    }

    @Test
    @DisplayName("PARTIAL outcome moves claim to ACCEPTED (awaiting remittance)")
    void testRecordOutcome_Partial_MovesToAccepted() {
        sampleClaim.setStatus(ClaimStatus.APPEALED);
        sampleDenial.setStatus(DenialStatus.APPEALED);

        ClaimAppeal appeal = new ClaimAppeal(sampleClaim, sampleDenial, 1, LocalDate.now().minusDays(15), LocalDate.now().plusDays(15), "L1", "biller");
        appeal.setId(202L);

        when(appeals.findWithReferencesById(202L)).thenReturn(Optional.of(appeal));
        when(currentUser.username()).thenReturn("biller");
        when(appeals.save(any(ClaimAppeal.class))).thenAnswer(inv -> inv.getArgument(0));

        RecordOutcomeRequest req = new RecordOutcomeRequest(
                AppealOutcome.PARTIAL,
                LocalDate.now().minusDays(1),
                new BigDecimal("800.00"),
                "Partial overturn"
        );

        AppealResponse res = appealService.recordOutcome(202L, req);

        assertThat(res.resultingClaimStatus()).isEqualTo(ClaimStatus.ACCEPTED);
        assertThat(sampleClaim.getStatus()).isEqualTo(ClaimStatus.ACCEPTED);
        assertThat(sampleDenial.getStatus()).isEqualTo(DenialStatus.RESOLVED);
    }

    @Test
    @DisplayName("UPHELD outcome on Level 1 returns claim to DENIED and denial to OPEN")
    void testRecordOutcome_Upheld_Level1_DenialReopens() {
        sampleClaim.setStatus(ClaimStatus.APPEALED);
        sampleDenial.setStatus(DenialStatus.APPEALED);

        ClaimAppeal appeal = new ClaimAppeal(sampleClaim, sampleDenial, 1, LocalDate.now().minusDays(15), LocalDate.now().plusDays(15), "L1", "biller");
        appeal.setId(203L);

        when(appeals.findWithReferencesById(203L)).thenReturn(Optional.of(appeal));
        when(currentUser.username()).thenReturn("biller");
        when(appeals.save(any(ClaimAppeal.class))).thenAnswer(inv -> inv.getArgument(0));

        RecordOutcomeRequest req = new RecordOutcomeRequest(
                AppealOutcome.UPHELD,
                LocalDate.now().minusDays(1),
                BigDecimal.ZERO,
                "Payer maintained original denial"
        );

        AppealResponse res = appealService.recordOutcome(203L, req);

        assertThat(res.resultingClaimStatus()).isEqualTo(ClaimStatus.DENIED);
        assertThat(sampleClaim.getStatus()).isEqualTo(ClaimStatus.DENIED);
        assertThat(sampleDenial.getStatus()).isEqualTo(DenialStatus.OPEN); // ready for Level 2
    }

    @Test
    @DisplayName("UPHELD outcome on Level 3 returns claim to DENIED and denial to WRITTEN_OFF")
    void testRecordOutcome_Upheld_Level3_DenialWrittenOff() {
        sampleClaim.setStatus(ClaimStatus.APPEALED);
        sampleDenial.setStatus(DenialStatus.APPEALED);

        ClaimAppeal appeal = new ClaimAppeal(sampleClaim, sampleDenial, 3, LocalDate.now().minusDays(15), LocalDate.now().plusDays(15), "L3", "biller");
        appeal.setId(204L);

        when(appeals.findWithReferencesById(204L)).thenReturn(Optional.of(appeal));
        when(currentUser.username()).thenReturn("biller");
        when(appeals.save(any(ClaimAppeal.class))).thenAnswer(inv -> inv.getArgument(0));

        RecordOutcomeRequest req = new RecordOutcomeRequest(
                AppealOutcome.UPHELD,
                LocalDate.now().minusDays(1),
                BigDecimal.ZERO,
                "External review upheld"
        );

        AppealResponse res = appealService.recordOutcome(204L, req);

        assertThat(res.resultingClaimStatus()).isEqualTo(ClaimStatus.DENIED);
        assertThat(sampleClaim.getStatus()).isEqualTo(ClaimStatus.DENIED);
        assertThat(sampleDenial.getStatus()).isEqualTo(DenialStatus.WRITTEN_OFF);
    }

    @Test
    @DisplayName("WITHDRAWN outcome returns claim to DENIED and denial to WRITTEN_OFF")
    void testRecordOutcome_Withdrawn() {
        sampleClaim.setStatus(ClaimStatus.APPEALED);
        sampleDenial.setStatus(DenialStatus.APPEALED);

        ClaimAppeal appeal = new ClaimAppeal(sampleClaim, sampleDenial, 1, LocalDate.now().minusDays(15), LocalDate.now().plusDays(15), "L1", "biller");
        appeal.setId(205L);

        when(appeals.findWithReferencesById(205L)).thenReturn(Optional.of(appeal));
        when(currentUser.username()).thenReturn("biller");
        when(appeals.save(any(ClaimAppeal.class))).thenAnswer(inv -> inv.getArgument(0));

        RecordOutcomeRequest req = new RecordOutcomeRequest(
                AppealOutcome.WITHDRAWN,
                LocalDate.now().minusDays(1),
                BigDecimal.ZERO,
                "Provider withdrew appeal"
        );

        AppealResponse res = appealService.recordOutcome(205L, req);

        assertThat(res.resultingClaimStatus()).isEqualTo(ClaimStatus.DENIED);
        assertThat(sampleClaim.getStatus()).isEqualTo(ClaimStatus.DENIED);
        assertThat(sampleDenial.getStatus()).isEqualTo(DenialStatus.WRITTEN_OFF);
    }

    @Test
    @DisplayName("Fails if appeal has already been decided")
    void testRecordOutcome_AlreadyDecided_ThrowsConflict() {
        ClaimAppeal appeal = new ClaimAppeal(sampleClaim, sampleDenial, 1, LocalDate.now().minusDays(15), LocalDate.now().plusDays(15), "L1", "biller");
        appeal.setId(206L);
        appeal.setOutcome(AppealOutcome.UPHELD);
        appeal.setDecidedOn(LocalDate.now().minusDays(5));

        when(appeals.findWithReferencesById(206L)).thenReturn(Optional.of(appeal));

        RecordOutcomeRequest req = new RecordOutcomeRequest(
                AppealOutcome.OVERTURNED,
                LocalDate.now().minusDays(1),
                new BigDecimal("1000.00"),
                "Re-decision attempt"
        );

        assertThatThrownBy(() -> appealService.recordOutcome(206L, req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("has already been decided");
    }

    // =========================================================================
    // 3. Rule 12: Generic Transition Bypass Prevention
    // =========================================================================

    @Test
    @DisplayName("Rule 12: Generic transition directly to APPEALED is rejected by ClaimStatusMachine")
    void testGenericTransition_ToAppealed_Blocked() {
        sampleClaim.setStatus(ClaimStatus.DENIED);

        TransitionRequest req = new TransitionRequest(ClaimStatus.APPEALED, "Direct bypass attempt", null, null);

        assertThatThrownBy(() -> statusMachine.apply(sampleClaim, req, "biller"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot transition directly to APPEALED via generic transition");
    }

    @Test
    @DisplayName("Rule 12: Generic transition from APPEALED to ACCEPTED/PAID is rejected by ClaimStatusMachine")
    void testGenericTransition_FromAppealed_Blocked() {
        sampleClaim.setStatus(ClaimStatus.APPEALED);

        TransitionRequest req = new TransitionRequest(ClaimStatus.ACCEPTED, "Direct bypass from APPEALED", null, null);

        assertThatThrownBy(() -> statusMachine.apply(sampleClaim, req, "biller"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot transition claim CLM-2026-000004 from APPEALED via generic transition");
    }
}

