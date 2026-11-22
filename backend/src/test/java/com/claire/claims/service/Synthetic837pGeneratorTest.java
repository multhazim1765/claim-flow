package com.claire.claims.service.edi;

import com.claire.claims.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Synthetic837pGeneratorTest {

    private Synthetic837pGenerator generator;
    private Payer payer;
    private Provider provider;
    private Patient patient;

    @BeforeEach
    void setUp() {
        generator = new Synthetic837pGenerator();

        payer = new Payer();
        payer.setId(1L);
        payer.setName("Aetna Health");
        payer.setPayerCode("AETNA");

        provider = new Provider();
        provider.setId(1L);
        provider.setFirstName("Sarah");
        provider.setLastName("Chen");
        provider.setNpi("1234567890");
        provider.setTaxId("998877665");

        patient = new Patient();
        patient.setId(1L);
        patient.setFirstName("James");
        patient.setLastName("Wilson");
        patient.setMrn("MRN-1001");
        patient.setDateOfBirth(LocalDate.of(1985, 4, 12));
        patient.setAddressLine1("123 Beacon St");
        patient.setCity("Boston");
        patient.setState("MA");
        patient.setPostalCode("02116");
    }

    @Test
    @DisplayName("Generates valid synthetic 837P with accurate envelopes, control numbers, and segment counts")
    void generatesValid837pFile() {
        Claim claim = new Claim();
        claim.setId(10L);
        claim.setClaimNumber("CLM-2026-000010");
        claim.setStatus(ClaimStatus.SUBMITTED);
        claim.setPayer(payer);
        claim.setProvider(provider);
        claim.setPatient(patient);
        claim.setServiceDateFrom(LocalDate.of(2026, 9, 1));
        claim.setServiceDateTo(LocalDate.of(2026, 9, 1));
        claim.setPlaceOfService("11");
        claim.setTotalCharge(new BigDecimal("250.00"));

        InsurancePolicy policy = new InsurancePolicy();
        policy.setMemberId("MEM-12345");
        policy.setPriority(PolicyPriority.PRIMARY);
        claim.setPolicy(policy);

        ClaimLine line = new ClaimLine();
        line.setLineNumber(1);
        line.setCptCode("99214");
        line.setModifiers("25");
        line.setUnits(1);
        line.setChargeAmount(new BigDecimal("250.00"));
        line.setServiceDate(LocalDate.of(2026, 9, 1));
        claim.getLines().add(line);

        ClaimDiagnosis diag = new ClaimDiagnosis();
        diag.setSequenceNo(1);
        diag.setIcd10Code("E11.9");
        diag.setDescription("Type 2 diabetes");
        claim.getDiagnoses().add(diag);

        OffsetDateTime now = OffsetDateTime.now();
        Synthetic837pGenerator.GeneratorResult result = generator.generate(List.of(claim), payer, 14L, now);

        assertThat(result.payload()).isNotEmpty();
        String[] lines = result.payload().split("~\\n");

        // 1. Verify ISA
        assertThat(lines[0]).startsWith("ISA*00*          *00*          *ZZ*CLAIMFLOW      *ZZ*AETNA          *");
        assertThat(lines[0]).contains("*T*:"); // Test mode indicator

        // 2. Verify GS
        assertThat(lines[1]).startsWith("GS*HC*CLAIMFLOW*AETNA*");
        assertThat(lines[1]).endsWith("*14*X*005010X222A1");

        // 3. Verify ST and BHT
        assertThat(lines[2]).isEqualTo("ST*837*0014*005010X222A1");
        assertThat(lines[3]).startsWith("BHT*0019*00*0014*");

        // 4. Verify SE, GE, IEA trailers
        String seLine = lines[lines.length - 3];
        String geLine = lines[lines.length - 2];
        String ieaLine = lines[lines.length - 1];

        assertThat(seLine).startsWith("SE*");
        assertThat(seLine).endsWith("*0014");
        int seReportedCount = Integer.parseInt(seLine.split("\\*")[1]);

        // Exact segment count verification: segments between ST and SE inclusive
        // lines index: 2 is ST, lines.length - 3 is SE
        int actualStToSeCount = (lines.length - 3) - 2 + 1;
        assertThat(seReportedCount).isEqualTo(actualStToSeCount);

        assertThat(geLine).isEqualTo("GE*1*14");
        assertThat(ieaLine).isEqualTo("IEA*1*" + result.interchangeControlNumber());

        // 5. Verify Claim Segments
        assertThat(result.payload()).contains("CLM*CLM-2026-000010*250.00***11:B:1*Y*A*Y*Y");
        assertThat(result.payload()).contains("HI*BK:E119");
        assertThat(result.payload()).contains("SV1*HC:99214:25*250.00*UN*1***1");
    }

    @Test
    @DisplayName("Generates multi-claim batch with distinct hierarchical levels")
    void generatesMultiClaimBatch() {
        Claim c1 = new Claim();
        c1.setId(1L);
        c1.setClaimNumber("CLM-1");
        c1.setStatus(ClaimStatus.SUBMITTED);
        c1.setPayer(payer);
        c1.setProvider(provider);
        c1.setPatient(patient);
        c1.setTotalCharge(new BigDecimal("100.00"));

        Claim c2 = new Claim();
        c2.setId(2L);
        c2.setClaimNumber("CLM-2");
        c2.setStatus(ClaimStatus.SUBMITTED);
        c2.setPayer(payer);
        c2.setProvider(provider);
        c2.setPatient(patient);
        c2.setTotalCharge(new BigDecimal("200.00"));

        Synthetic837pGenerator.GeneratorResult result = generator.generate(List.of(c1, c2), payer, 5L, OffsetDateTime.now());

        assertThat(result.payload()).contains("CLM*CLM-1*100.00");
        assertThat(result.payload()).contains("CLM*CLM-2*200.00");
        assertThat(result.payload()).contains("HL*1**20*1");
        assertThat(result.payload()).contains("HL*2*1*22*0");
        assertThat(result.payload()).contains("HL*3**20*1");
        assertThat(result.payload()).contains("HL*4*3*22*0");
    }
}

