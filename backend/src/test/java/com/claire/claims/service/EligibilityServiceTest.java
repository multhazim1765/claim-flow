package com.claire.claims.service;

import com.claire.claims.common.ApiExceptions.NotFoundException;
import com.claire.claims.domain.*;
import com.claire.claims.dto.EligibilityDtos.EligibilityCheckRequest;
import com.claire.claims.dto.EligibilityDtos.EligibilityCheckResponse;
import com.claire.claims.repository.EligibilityCheckRepository;
import com.claire.claims.repository.InsurancePolicyRepository;
import com.claire.claims.repository.PatientRepository;
import com.claire.claims.repository.PayerRepository;
import com.claire.claims.security.CurrentUserProvider;
import com.claire.claims.service.eligibility.EligibilityGateway;
import com.claire.claims.service.eligibility.EligibilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EligibilityServiceTest {

    @Mock
    private EligibilityCheckRepository eligibilityChecks;
    @Mock
    private PatientRepository patients;
    @Mock
    private PayerRepository payers;
    @Mock
    private InsurancePolicyRepository policies;
    @Mock
    private EligibilityGateway gateway;
    @Mock
    private CurrentUserProvider currentUser;

    @InjectMocks
    private EligibilityService service;

    private Patient patient;
    private Payer payer;
    private InsurancePolicy policy;

    @BeforeEach
    void setUp() {
        patient = new Patient();
        patient.setId(1L);
        patient.setMrn("MRN-100001");
        patient.setFirstName("Robert");
        patient.setLastName("Castellano");
        patient.setDateOfBirth(LocalDate.of(1958, 3, 14));

        payer = new Payer();
        payer.setId(3L);
        payer.setPayerCode("NOVITAS");
        payer.setName("Medicare Part B - Novitas");
        payer.setPlanType(PlanType.MEDICARE);

        policy = new InsurancePolicy();
        policy.setId(1L);
        policy.setPatient(patient);
        policy.setPayer(payer);
        policy.setMemberId("MB1A2B3C4D5");
        policy.setPriority(PolicyPriority.PRIMARY);
        policy.setEffectiveDate(LocalDate.of(2023, 4, 1));
    }

    @Test
    void checkEligibility_cacheMiss_callsGatewayAndPersists() {
        LocalDate serviceDate = LocalDate.of(2026, 9, 20);
        EligibilityCheckRequest req = new EligibilityCheckRequest(1L, 3L, 1L, serviceDate, "30", false);

        when(patients.findById(1L)).thenReturn(Optional.of(patient));
        when(payers.findById(3L)).thenReturn(Optional.of(payer));
        when(policies.findById(1L)).thenReturn(Optional.of(policy));
        when(eligibilityChecks.findMatchingCachedChecks(
                eq(1L), eq(3L), eq(1L), eq(serviceDate), eq("30"), anyString(), any(OffsetDateTime.class)))
                .thenReturn(Collections.emptyList());

        EligibilityGateway.GatewayResponse gwResp = new EligibilityGateway.GatewayResponse(
                true,
                LocalDate.of(2023, 4, 1),
                null,
                new BigDecimal("30.00"),
                20,
                new BigDecimal("300.00"),
                new BigDecimal("100.00"),
                new BigDecimal("3000.00"),
                "ISA*RAW...",
                "SIMULATED_DEFAULT"
        );
        when(gateway.verify(eq(patient), eq(payer), eq(policy), eq(serviceDate), eq("30"))).thenReturn(gwResp);
        when(currentUser.username()).thenReturn("biller");

        when(eligibilityChecks.save(any(EligibilityCheck.class))).thenAnswer(invocation -> {
            EligibilityCheck c = invocation.getArgument(0);
            c.setId(100L);
            return c;
        });

        EligibilityCheckResponse resp = service.checkEligibility(req);

        assertThat(resp).isNotNull();
        assertThat(resp.id()).isEqualTo(100L);
        assertThat(resp.active()).isTrue();
        assertThat(resp.copay()).isEqualByComparingTo("30.00");
        assertThat(resp.cached()).isFalse();
        assertThat(resp.benefitSource()).isEqualTo("SIMULATED_DEFAULT");
        assertThat(resp.sourceLabel()).isEqualTo("SIMULATED — DEFAULT PROFILE");
        assertThat(resp.patientName()).isEqualTo("Castellano, Robert");

        verify(gateway).verify(eq(patient), eq(payer), eq(policy), eq(serviceDate), eq("30"));
        verify(eligibilityChecks).save(any(EligibilityCheck.class));
    }

    @Test
    void checkEligibility_cacheHit_returnsCachedWithoutGatewayCall() {
        LocalDate serviceDate = LocalDate.of(2026, 9, 20);
        EligibilityCheckRequest req = new EligibilityCheckRequest(1L, 3L, 1L, serviceDate, "30", false);

        EligibilityCheck cachedCheck = new EligibilityCheck();
        cachedCheck.setId(99L);
        cachedCheck.setPatient(patient);
        cachedCheck.setPayer(payer);
        cachedCheck.setPolicy(policy);
        cachedCheck.setServiceDate(serviceDate);
        cachedCheck.setServiceTypeCode("30");
        cachedCheck.setActive(true);
        cachedCheck.setCheckedAt(OffsetDateTime.now().minusHours(2));
        cachedCheck.setCopay(new BigDecimal("30.00"));
        cachedCheck.setBenefitSource("SIMULATED_DEFAULT");
        cachedCheck.setRawResponse("ISA*CACHED...");
        cachedCheck.setCheckedBy("dana");

        when(patients.findById(1L)).thenReturn(Optional.of(patient));
        when(payers.findById(3L)).thenReturn(Optional.of(payer));
        when(policies.findById(1L)).thenReturn(Optional.of(policy));
        when(eligibilityChecks.findMatchingCachedChecks(
                eq(1L), eq(3L), eq(1L), eq(serviceDate), eq("30"), anyString(), any(OffsetDateTime.class)))
                .thenReturn(List.of(cachedCheck));

        EligibilityCheckResponse resp = service.checkEligibility(req);

        assertThat(resp).isNotNull();
        assertThat(resp.id()).isEqualTo(99L);
        assertThat(resp.cached()).isTrue();
        assertThat(resp.sourceLabel()).isEqualTo("CACHED SIMULATED RESPONSE");
        verifyNoInteractions(gateway);
        verify(eligibilityChecks, never()).save(any());
    }

    @Test
    void checkEligibility_forceRefresh_bypassesCache() {
        LocalDate serviceDate = LocalDate.of(2026, 9, 20);
        EligibilityCheckRequest req = new EligibilityCheckRequest(1L, 3L, 1L, serviceDate, "30", true);

        when(patients.findById(1L)).thenReturn(Optional.of(patient));
        when(payers.findById(3L)).thenReturn(Optional.of(payer));
        when(policies.findById(1L)).thenReturn(Optional.of(policy));

        EligibilityGateway.GatewayResponse gwResp = new EligibilityGateway.GatewayResponse(
                true, LocalDate.of(2023, 4, 1), null, new BigDecimal("30.00"), 20,
                new BigDecimal("300.00"), new BigDecimal("100.00"), new BigDecimal("3000.00"),
                "ISA*REFRESHED...", "SIMULATED_DEFAULT"
        );
        when(gateway.verify(any(), any(), any(), any(), any())).thenReturn(gwResp);
        when(currentUser.username()).thenReturn("biller");
        when(eligibilityChecks.save(any())).thenAnswer(invocation -> {
            EligibilityCheck c = invocation.getArgument(0);
            c.setId(101L);
            return c;
        });

        EligibilityCheckResponse resp = service.checkEligibility(req);

        assertThat(resp.id()).isEqualTo(101L);
        assertThat(resp.cached()).isFalse();
        verify(gateway).verify(any(), any(), any(), any(), any());
    }

    @Test
    void checkEligibility_fingerprintChanges_whenMemberIdOrPlanTypeChanges() {
        String fpOriginal = EligibilityService.computeCacheFingerprint(payer, policy);

        // Change member ID
        policy.setMemberId("NEW-MEMBER-ID");
        String fpNewMember = EligibilityService.computeCacheFingerprint(payer, policy);
        assertThat(fpNewMember).isNotEqualTo(fpOriginal);

        // Change plan type
        payer.setPlanType(PlanType.COMMERCIAL);
        String fpNewPlan = EligibilityService.computeCacheFingerprint(payer, policy);
        assertThat(fpNewPlan).isNotEqualTo(fpNewMember);

        // Add demo benefit overrides
        policy.setDemoCopay(new BigDecimal("15.00"));
        policy.setDemoCoinsurancePercent(10);
        policy.setDemoDeductibleTotal(new BigDecimal("100.00"));
        policy.setDemoDeductibleRemaining(BigDecimal.ZERO);
        policy.setDemoOutOfPocketRemaining(new BigDecimal("500.00"));
        String fpDemo = EligibilityService.computeCacheFingerprint(payer, policy);
        assertThat(fpDemo).isNotEqualTo(fpNewPlan);
    }

    @Test
    void checkEligibility_patientNotFound_throwsNotFoundException() {
        EligibilityCheckRequest req = new EligibilityCheckRequest(999L, 3L, null, LocalDate.now(), "30", false);
        when(patients.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkEligibility(req))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Patient 999 was not found");
    }

    @Test
    void checkEligibility_payerNotFound_throwsNotFoundException() {
        EligibilityCheckRequest req = new EligibilityCheckRequest(1L, 999L, null, LocalDate.now(), "30", false);
        when(patients.findById(1L)).thenReturn(Optional.of(patient));
        when(payers.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkEligibility(req))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Payer 999 was not found");
    }
}

