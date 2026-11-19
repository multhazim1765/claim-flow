package com.claire.claims.service;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.common.ApiExceptions.ConflictException;
import com.claire.claims.common.ApiExceptions.NotFoundException;
import com.claire.claims.domain.*;
import com.claire.claims.dto.ClaimDtos.PageResponse;
import com.claire.claims.dto.DenialDtos.*;
import com.claire.claims.repository.ClaimDenialRepository;
import com.claire.claims.repository.ClaimRepository;
import com.claire.claims.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DenialServiceTest {

    @Mock
    private ClaimDenialRepository claimDenials;
    @Mock
    private ClaimRepository claims;
    @Mock
    private CurrentUserProvider currentUser;

    @InjectMocks
    private DenialService denialService;

    private Claim sampleClaim;
    private Payer samplePayer;
    private Patient samplePatient;

    @BeforeEach
    void setUp() {
        samplePayer = new Payer();
        samplePayer.setId(2L);
        samplePayer.setName("Aetna Health");

        samplePatient = new Patient();
        samplePatient.setId(10L);
        samplePatient.setFirstName("Robert");
        samplePatient.setLastName("Martinez");
        samplePatient.setMrn("MRN-100004");

        sampleClaim = new Claim();
        sampleClaim.setId(4L);
        sampleClaim.setClaimNumber("CLM-2026-000004");
        sampleClaim.setPayer(samplePayer);
        sampleClaim.setPatient(samplePatient);
        sampleClaim.setStatus(ClaimStatus.DENIED);
        sampleClaim.setTotalCharge(new BigDecimal("1450.00"));
    }

    @Test
    @DisplayName("Successfully creates a denial record for a DENIED claim")
    void testCreateDenial_Success() {
        when(claims.findWithReferencesById(4L)).thenReturn(Optional.of(sampleClaim));
        when(claimDenials.existsByClaimIdAndStatusIn(eq(4L), anyCollection())).thenReturn(false);
        when(currentUser.username()).thenReturn("biller");
        when(claimDenials.save(any(ClaimDenial.class))).thenAnswer(invocation -> {
            ClaimDenial d = invocation.getArgument(0);
            d.setId(101L);
            return d;
        });

        CreateDenialRequest request = new CreateDenialRequest(
                4L,
                2L,
                "CO-197",
                "N24",
                DenialCategory.AUTHORIZATION,
                "Precertification absent",
                LocalDate.now().minusDays(5),
                LocalDate.now().plusDays(30),
                new BigDecimal("1450.00")
        );

        DenialResponse response = denialService.createDenial(request);

        assertThat(response).isNotNull();
        assertThat(response.claimId()).isEqualTo(4L);
        assertThat(response.claimNumber()).isEqualTo("CLM-2026-000004");
        assertThat(response.payerId()).isEqualTo(2L);
        assertThat(response.denialCode()).isEqualTo("CO-197");
        assertThat(response.remittanceCode()).isEqualTo("N24");
        assertThat(response.denialCategory()).isEqualTo(DenialCategory.AUTHORIZATION);
        assertThat(response.amount()).isEqualByComparingTo("1450.00");
        assertThat(response.createdBy()).isEqualTo("biller");
        assertThat(response.status()).isEqualTo(DenialStatus.OPEN);

        ArgumentCaptor<ClaimDenial> captor = ArgumentCaptor.forClass(ClaimDenial.class);
        verify(claimDenials).save(captor.capture());
        ClaimDenial saved = captor.getValue();
        assertThat(saved.getCreatedBy()).isEqualTo("biller");
        assertThat(saved.getDenialCode()).isEqualTo("CO-197");
    }

    @Test
    @DisplayName("Fails when claim is not found")
    void testCreateDenial_ClaimNotFound() {
        when(claims.findWithReferencesById(99L)).thenReturn(Optional.empty());

        CreateDenialRequest request = new CreateDenialRequest(
                99L, 2L, "CO-16", null, DenialCategory.BILLING_CODING,
                "Lacks information", LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal("100.00")
        );

        assertThatThrownBy(() -> denialService.createDenial(request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Claim 99 was not found");
    }

    @Test
    @DisplayName("Fails when claim is not in DENIED status")
    void testCreateDenial_ClaimNotDenied() {
        sampleClaim.setStatus(ClaimStatus.ACCEPTED);
        when(claims.findWithReferencesById(4L)).thenReturn(Optional.of(sampleClaim));

        CreateDenialRequest request = new CreateDenialRequest(
                4L, 2L, "CO-197", null, DenialCategory.AUTHORIZATION,
                "No precert", LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal("100.00")
        );

        assertThatThrownBy(() -> denialService.createDenial(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Claim must be in DENIED status");
    }

    @Test
    @DisplayName("Fails when payer does not match claim's payer")
    void testCreateDenial_PayerMismatch() {
        when(claims.findWithReferencesById(4L)).thenReturn(Optional.of(sampleClaim));

        CreateDenialRequest request = new CreateDenialRequest(
                4L, 999L, "CO-197", null, DenialCategory.AUTHORIZATION,
                "No precert", LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal("100.00")
        );

        assertThatThrownBy(() -> denialService.createDenial(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("belongs to payer Aetna Health (ID: 2), not payer ID 999");
    }

    @Test
    @DisplayName("Fails when an active denial already exists for the claim")
    void testCreateDenial_DuplicateActiveDenial() {
        when(claims.findWithReferencesById(4L)).thenReturn(Optional.of(sampleClaim));
        when(claimDenials.existsByClaimIdAndStatusIn(eq(4L), anyCollection())).thenReturn(true);

        CreateDenialRequest request = new CreateDenialRequest(
                4L, 2L, "CO-197", null, DenialCategory.AUTHORIZATION,
                "No precert", LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal("100.00")
        );

        assertThatThrownBy(() -> denialService.createDenial(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("An active denial record already exists for claim CLM-2026-000004");
    }

    @Test
    @DisplayName("Fails when denial amount exceeds claim total charge")
    void testCreateDenial_AmountExceedsCharge() {
        when(claims.findWithReferencesById(4L)).thenReturn(Optional.of(sampleClaim));

        CreateDenialRequest request = new CreateDenialRequest(
                4L, 2L, "CO-197", null, DenialCategory.AUTHORIZATION,
                "No precert", LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal("2000.00")
        );

        assertThatThrownBy(() -> denialService.createDenial(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot exceed claim total charge");
    }

    @Test
    @DisplayName("Fails when denial date is in the future")
    void testCreateDenial_FutureDenialDate() {
        when(claims.findWithReferencesById(4L)).thenReturn(Optional.of(sampleClaim));

        CreateDenialRequest request = new CreateDenialRequest(
                4L, 2L, "CO-197", null, DenialCategory.AUTHORIZATION,
                "No precert", LocalDate.now().plusDays(2), LocalDate.now().plusDays(30), new BigDecimal("100.00")
        );

        assertThatThrownBy(() -> denialService.createDenial(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Denial date cannot be in the future");
    }

    @Test
    @DisplayName("Fails when appeal deadline is before denial date")
    void testCreateDenial_DeadlineBeforeDenialDate() {
        when(claims.findWithReferencesById(4L)).thenReturn(Optional.of(sampleClaim));

        CreateDenialRequest request = new CreateDenialRequest(
                4L, 2L, "CO-197", null, DenialCategory.AUTHORIZATION,
                "No precert", LocalDate.now().minusDays(5), LocalDate.now().minusDays(10), new BigDecimal("100.00")
        );

        assertThatThrownBy(() -> denialService.createDenial(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Appeal deadline cannot be before the denial date");
    }

    @Test
    @DisplayName("Accurately calculates urgency and does not report positive remaining days for expired deadline")
    void testUrgency_ExpiredDeadline() {
        LocalDate expiredDeadline = LocalDate.now().minusDays(20);
        ClaimDenial denial = new ClaimDenial(
                sampleClaim, samplePayer, "CO-197", "N24", DenialCategory.AUTHORIZATION,
                "Reason", LocalDate.now().minusDays(50), expiredDeadline, new BigDecimal("1450.00"), "biller"
        );
        denial.setId(1L);

        when(claimDenials.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(denial), PageRequest.of(0, 20), 1));

        PageResponse<DenialWorklistItem> result = denialService.search(null, null, null, null, PageRequest.of(0, 20));

        assertThat(result.content()).hasSize(1);
        DenialWorklistItem item = result.content().get(0);
        assertThat(item.urgency()).isEqualTo("OVERDUE");
        assertThat(item.daysRemaining()).isLessThan(0);
        assertThat(item.daysRemaining()).isEqualTo(-20);
    }

    @Test
    @DisplayName("Calculates CRITICAL, WARNING, and NORMAL urgencies correctly")
    void testUrgency_Categories() {
        LocalDate today = LocalDate.now();

        ClaimDenial critical = new ClaimDenial(sampleClaim, samplePayer, "CO-16", null, DenialCategory.BILLING_CODING, "R", today.minusDays(5), today.plusDays(3), new BigDecimal("100.00"), "biller");
        ClaimDenial warning = new ClaimDenial(sampleClaim, samplePayer, "CO-29", null, DenialCategory.TIMELY_FILING, "R", today.minusDays(5), today.plusDays(15), new BigDecimal("200.00"), "biller");
        ClaimDenial normal = new ClaimDenial(sampleClaim, samplePayer, "CO-96", null, DenialCategory.COVERAGE_ELIGIBILITY, "R", today.minusDays(5), today.plusDays(45), new BigDecimal("300.00"), "biller");

        when(claimDenials.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(critical, warning, normal), PageRequest.of(0, 20), 3));

        PageResponse<DenialWorklistItem> result = denialService.search(null, null, null, null, PageRequest.of(0, 20));

        assertThat(result.content()).hasSize(3);
        assertThat(result.content().get(0).urgency()).isEqualTo("CRITICAL");
        assertThat(result.content().get(0).daysRemaining()).isEqualTo(3);

        assertThat(result.content().get(1).urgency()).isEqualTo("WARNING");
        assertThat(result.content().get(1).daysRemaining()).isEqualTo(15);

        assertThat(result.content().get(2).urgency()).isEqualTo("NORMAL");
        assertThat(result.content().get(2).daysRemaining()).isEqualTo(45);
    }

    @Test
    @DisplayName("Summary correctly aggregates open counts, amounts, urgencies, and category breakdown")
    void testGetSummary() {
        LocalDate today = LocalDate.now();

        ClaimDenial d1 = new ClaimDenial(sampleClaim, samplePayer, "CO-197", "N24", DenialCategory.AUTHORIZATION, "Auth", today.minusDays(10), today.minusDays(2), new BigDecimal("1450.00"), "biller");
        ClaimDenial d2 = new ClaimDenial(sampleClaim, samplePayer, "CO-16", null, DenialCategory.BILLING_CODING, "Coding", today.minusDays(5), today.plusDays(5), new BigDecimal("350.00"), "biller");

        when(claimDenials.findAll(any(Specification.class))).thenReturn(List.of(d1, d2));

        DenialSummaryResponse summary = denialService.getSummary();

        assertThat(summary.totalOpenDenials()).isEqualTo(2);
        assertThat(summary.totalOpenDeniedAmount()).isEqualByComparingTo("1800.00");
        assertThat(summary.overdueDeadlines()).isEqualTo(1);
        assertThat(summary.criticalDeadlines()).isEqualTo(1);
        assertThat(summary.warningDeadlines()).isEqualTo(0);
        assertThat(summary.normalDeadlines()).isEqualTo(0);

        CategoryBreakdown auth = summary.byCategory().get("AUTHORIZATION");
        assertThat(auth.count()).isEqualTo(1);
        assertThat(auth.amount()).isEqualByComparingTo("1450.00");

        CategoryBreakdown coding = summary.byCategory().get("BILLING_CODING");
        assertThat(coding.count()).isEqualTo(1);
        assertThat(coding.amount()).isEqualByComparingTo("350.00");
    }
}

