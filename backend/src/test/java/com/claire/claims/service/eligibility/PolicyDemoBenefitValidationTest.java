package com.claire.claims.service.eligibility;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.domain.InsurancePolicy;
import com.claire.claims.domain.Patient;
import com.claire.claims.domain.Payer;
import com.claire.claims.domain.PolicyPriority;
import com.claire.claims.dto.ReferenceDtos.PolicyRequest;
import com.claire.claims.dto.ReferenceDtos.PolicyResponse;
import com.claire.claims.repository.InsurancePolicyRepository;
import com.claire.claims.repository.PatientRepository;
import com.claire.claims.repository.PayerRepository;
import com.claire.claims.service.ClaimMapper;
import com.claire.claims.service.ReferenceDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyDemoBenefitValidationTest {

    @Mock
    private PatientRepository patients;
    @Mock
    private PayerRepository payers;
    @Mock
    private InsurancePolicyRepository policies;
    @Mock
    private ClaimMapper mapper;

    @InjectMocks
    private ReferenceDataService service;

    private Patient patient;
    private Payer payer;

    @BeforeEach
    void setUp() {
        patient = new Patient();
        patient.setId(1L);
        payer = new Payer();
        payer.setId(2L);
        payer.setName("Test Payer");
    }

    @Test
    void createPolicy_rejectsPartialDemoBenefitProfile() {
        // Only 2 of 5 demo fields provided
        PolicyRequest req = new PolicyRequest(
                1L, 2L, "MB-101", null, PolicyPriority.PRIMARY,
                LocalDate.of(2025, 1, 1), null, true,
                new BigDecimal("25.00"), 20, null, null, null);

        assertThatThrownBy(() -> service.createPolicy(req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Incomplete demo benefit profile");
    }

    @Test
    void createPolicy_rejectsRemainingDeductibleGreaterThanTotal() {
        PolicyRequest req = new PolicyRequest(
                1L, 2L, "MB-101", null, PolicyPriority.PRIMARY,
                LocalDate.of(2025, 1, 1), null, true,
                new BigDecimal("25.00"), 20,
                new BigDecimal("500.00"), new BigDecimal("600.00"), new BigDecimal("2000.00"));

        assertThatThrownBy(() -> service.createPolicy(req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot exceed total deductible");
    }

    @Test
    void createPolicy_rejectsCoinsuranceAbove100() {
        PolicyRequest req = new PolicyRequest(
                1L, 2L, "MB-101", null, PolicyPriority.PRIMARY,
                LocalDate.of(2025, 1, 1), null, true,
                new BigDecimal("25.00"), 120,
                new BigDecimal("500.00"), new BigDecimal("100.00"), new BigDecimal("2000.00"));

        assertThatThrownBy(() -> service.createPolicy(req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("between 0 and 100");
    }

    @Test
    void createPolicy_acceptsCompleteValidDemoBenefitProfile() {
        PolicyRequest req = new PolicyRequest(
                1L, 2L, "MB-101", null, PolicyPriority.PRIMARY,
                LocalDate.of(2025, 1, 1), null, true,
                new BigDecimal("25.00"), 20,
                new BigDecimal("500.00"), new BigDecimal("100.00"), new BigDecimal("2000.00"));

        when(patients.findById(1L)).thenReturn(Optional.of(patient));
        when(payers.findById(2L)).thenReturn(Optional.of(payer));
        when(policies.save(any(InsurancePolicy.class))).thenAnswer(inv -> {
            InsurancePolicy p = inv.getArgument(0);
            p.setId(50L);
            return p;
        });

        PolicyResponse expectedResp = new PolicyResponse(
                50L, 1L, 2L, "Test Payer", "MB-101", null, PolicyPriority.PRIMARY,
                LocalDate.of(2025, 1, 1), null, true, true,
                new BigDecimal("25.00"), 20, new BigDecimal("500.00"),
                new BigDecimal("100.00"), new BigDecimal("2000.00"), true);
        when(mapper.toPolicy(any(InsurancePolicy.class))).thenReturn(expectedResp);

        PolicyResponse resp = service.createPolicy(req);

        assertThat(resp).isNotNull();
        assertThat(resp.hasCustomDemoProfile()).isTrue();
        assertThat(resp.demoCopay()).isEqualByComparingTo("25.00");
    }
}

