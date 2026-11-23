package com.claire.claims.service.eligibility;

import com.claire.claims.domain.InsurancePolicy;
import com.claire.claims.domain.Patient;
import com.claire.claims.domain.Payer;
import com.claire.claims.domain.PlanType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedEligibilityGatewayTest {

    private SimulatedEligibilityGateway gateway;
    private Patient patient;
    private Payer medicarePayer;
    private Payer commercialPayer;
    private InsurancePolicy policy;

    @BeforeEach
    void setUp() {
        gateway = new SimulatedEligibilityGateway();

        patient = new Patient();
        patient.setId(10L);
        patient.setMrn("MRN-TEST-1");
        patient.setFirstName("Alice");
        patient.setLastName("Smith");
        patient.setDateOfBirth(LocalDate.of(1985, 5, 20));

        medicarePayer = new Payer();
        medicarePayer.setId(1L);
        medicarePayer.setPayerCode("MED-001");
        medicarePayer.setName("Medicare Demo Plan");
        medicarePayer.setPlanType(PlanType.MEDICARE);

        commercialPayer = new Payer();
        commercialPayer.setId(2L);
        commercialPayer.setPayerCode("COMM-001");
        commercialPayer.setName("Aetna Commercial Demo");
        commercialPayer.setPlanType(PlanType.COMMERCIAL);

        policy = new InsurancePolicy();
        policy.setId(100L);
        policy.setPatient(patient);
        policy.setPayer(medicarePayer);
        policy.setMemberId("MBR-12345");
        policy.setEffectiveDate(LocalDate.of(2025, 1, 1));
        policy.setActive(true);
    }

    @Test
    void verify_usesCustomDemoProfile_whenComplete() {
        // Configure complete custom demo benefits
        policy.setDemoCopay(new BigDecimal("15.50"));
        policy.setDemoCoinsurancePercent(10);
        policy.setDemoDeductibleTotal(new BigDecimal("400.00"));
        policy.setDemoDeductibleRemaining(new BigDecimal("120.00"));
        policy.setDemoOutOfPocketRemaining(new BigDecimal("1800.00"));

        EligibilityGateway.GatewayResponse resp = gateway.verify(
                patient, medicarePayer, policy, LocalDate.of(2026, 6, 15), "30");

        assertThat(resp.active()).isTrue();
        assertThat(resp.benefitSource()).isEqualTo("SIMULATED_CUSTOM_DEMO_PROFILE");
        assertThat(resp.copay()).isEqualByComparingTo("15.50");
        assertThat(resp.coinsurancePercent()).isEqualTo(10);
        assertThat(resp.deductibleTotal()).isEqualByComparingTo("400.00");
        assertThat(resp.deductibleRemaining()).isEqualByComparingTo("120.00");
        assertThat(resp.outOfPocketRemaining()).isEqualByComparingTo("1800.00");

        // Verify raw X12 contains custom amounts
        assertThat(resp.rawResponse()).contains("400.00*120.00");
        assertThat(resp.rawResponse()).contains("15.50");
        assertThat(resp.rawResponse()).contains(".10");
    }

    @Test
    void verify_usesSimulatorDefault_whenDemoProfileOmitted() {
        // Policy with no demo benefit overrides
        EligibilityGateway.GatewayResponse resp = gateway.verify(
                patient, medicarePayer, policy, LocalDate.of(2026, 6, 15), "30");

        assertThat(resp.active()).isTrue();
        assertThat(resp.benefitSource()).isEqualTo("SIMULATED_DEFAULT");
        // Verify Medicare fictional educational profile (distinct from historical $240 CMS)
        assertThat(resp.copay()).isEqualByComparingTo("30.00");
        assertThat(resp.coinsurancePercent()).isEqualTo(20);
        assertThat(resp.deductibleTotal()).isEqualByComparingTo("300.00");
        assertThat(resp.deductibleRemaining()).isEqualByComparingTo("100.00");
        assertThat(resp.outOfPocketRemaining()).isEqualByComparingTo("3000.00");
    }

    @Test
    void verify_commercialDefault_returnsFictionalCommercialProfile() {
        policy.setPayer(commercialPayer);
        EligibilityGateway.GatewayResponse resp = gateway.verify(
                patient, commercialPayer, policy, LocalDate.of(2026, 6, 15), "30");

        assertThat(resp.benefitSource()).isEqualTo("SIMULATED_DEFAULT");
        assertThat(resp.copay()).isEqualByComparingTo("40.00");
        assertThat(resp.coinsurancePercent()).isEqualTo(20);
        assertThat(resp.deductibleTotal()).isEqualByComparingTo("1500.00");
    }

    @Test
    void verify_embedsRequestedServiceTypeCode_inX12Payload() {
        EligibilityGateway.GatewayResponse resp = gateway.verify(
                patient, medicarePayer, policy, LocalDate.of(2026, 6, 15), "33");

        // Service type code 33 (Chiropractic) must appear in the EB segments
        assertThat(resp.rawResponse()).contains("EB*1**33*");
        assertThat(resp.rawResponse()).contains("EB*B**33*");
        assertThat(resp.rawResponse()).contains("EB*C**33*");
    }

    @Test
    void verify_inactivePolicy_returnsZeroBenefits() {
        policy.setActive(false);
        EligibilityGateway.GatewayResponse resp = gateway.verify(
                patient, medicarePayer, policy, LocalDate.of(2026, 6, 15), "30");

        assertThat(resp.active()).isFalse();
        assertThat(resp.copay()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resp.coinsurancePercent()).isEqualTo(0);
        assertThat(resp.deductibleTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resp.rawResponse()).contains("EB*6**30*"); // 6 = Inactive
    }
}

