package com.claire.claims.service;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.common.ApiExceptions.ConflictException;
import com.claire.claims.domain.*;
import com.claire.claims.dto.Edi837pDtos.*;
import com.claire.claims.repository.ClaimRepository;
import com.claire.claims.repository.EdiExportBatchClaimRepository;
import com.claire.claims.repository.EdiExportBatchRepository;
import com.claire.claims.repository.PayerRepository;
import com.claire.claims.service.edi.Edi837pExportService;
import com.claire.claims.service.edi.Synthetic837pGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Edi837pExportServiceTest {

    @Mock private ClaimRepository claimRepository;
    @Mock private PayerRepository payerRepository;
    @Mock private EdiExportBatchRepository batchRepository;
    @Mock private EdiExportBatchClaimRepository batchClaimRepository;
    @Mock private Synthetic837pGenerator generator;

    private Edi837pExportService exportService;
    private Payer payer;
    private Claim validClaim;

    @BeforeEach
    void setUp() {
        exportService = new Edi837pExportService(
                claimRepository,
                payerRepository,
                batchRepository,
                batchClaimRepository,
                generator
        );

        payer = new Payer();
        payer.setId(1L);
        payer.setName("BCBS");
        payer.setPayerCode("BCBS01");

        Provider prov = new Provider();
        prov.setId(1L);
        prov.setFirstName("John");
        prov.setLastName("Doe");
        prov.setNpi("1234567890");
        prov.setTaxId("123456789");

        Patient pat = new Patient();
        pat.setId(1L);
        pat.setFirstName("Jane");
        pat.setLastName("Smith");
        pat.setMrn("MRN-200");
        pat.setDateOfBirth(LocalDate.of(1990, 1, 1));

        InsurancePolicy policy = new InsurancePolicy();
        policy.setMemberId("M-999");
        policy.setPayer(payer);

        validClaim = new Claim();
        validClaim.setId(10L);
        validClaim.setClaimNumber("CLM-10");
        validClaim.setStatus(ClaimStatus.SUBMITTED);
        validClaim.setPayer(payer);
        validClaim.setProvider(prov);
        validClaim.setPatient(pat);
        validClaim.setPolicy(policy);
        validClaim.setServiceDateFrom(LocalDate.of(2026, 9, 1));
        validClaim.setServiceDateTo(LocalDate.of(2026, 9, 1));
        validClaim.setTotalCharge(new BigDecimal("150.00"));

        ClaimLine line = new ClaimLine();
        line.setLineNumber(1);
        line.setCptCode("99213");
        line.setUnits(1);
        line.setChargeAmount(new BigDecimal("150.00"));
        validClaim.getLines().add(line);

        ClaimDiagnosis diag = new ClaimDiagnosis();
        diag.setSequenceNo(1);
        diag.setIcd10Code("J06.9");
        validClaim.getDiagnoses().add(diag);
    }

    @Test
    @DisplayName("Validation succeeds for eligible SUBMITTED claim with complete data")
    void validateClaimsSuccess() {
        when(claimRepository.findWithReferencesById(10L)).thenReturn(Optional.of(validClaim));

        EdiValidationResponse response = exportService.validateClaims(new ValidateClaimsRequest(List.of(10L), 1L));

        assertThat(response.allValid()).isTrue();
        assertThat(response.validCount()).isEqualTo(1);
        assertThat(response.errorCount()).isEqualTo(0);
        assertThat(response.results().get(0).errors()).isEmpty();
    }

    @Test
    @DisplayName("Validation fails when claim is in DRAFT status or missing NPI")
    void validateClaimsFailsForDraft() {
        validClaim.setStatus(ClaimStatus.DRAFT);
        validClaim.getProvider().setNpi("INVALID");
        when(claimRepository.findWithReferencesById(10L)).thenReturn(Optional.of(validClaim));

        EdiValidationResponse response = exportService.validateClaims(new ValidateClaimsRequest(List.of(10L), 1L));

        assertThat(response.allValid()).isFalse();
        assertThat(response.errorCount()).isEqualTo(1);
        assertThat(response.results().get(0).errors())
                .anyMatch(e -> e.contains("only SUBMITTED claims are eligible"))
                .anyMatch(e -> e.contains("Invalid provider NPI"));
    }

    @Test
    @DisplayName("Export rejects mixed payer batch")
    void exportRejectsMixedPayerBatch() {
        Claim c2 = new Claim();
        c2.setId(20L);
        Payer payer2 = new Payer();
        payer2.setId(2L);
        c2.setPayer(payer2);

        when(claimRepository.findWithReferencesById(10L)).thenReturn(Optional.of(validClaim));
        when(claimRepository.findWithReferencesById(20L)).thenReturn(Optional.of(c2));

        assertThatThrownBy(() -> exportService.export837p(new Export837pRequest(List.of(10L, 20L), 1L, false, null), "biller"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Mixed-payer export batches are not supported");
    }

    @Test
    @DisplayName("Export rejects duplicate claims unless allowRebill is true")
    void exportRejectsPreviouslyExportedClaim() {
        when(claimRepository.findWithReferencesById(10L)).thenReturn(Optional.of(validClaim));
        when(payerRepository.findById(1L)).thenReturn(Optional.of(payer));
        when(batchClaimRepository.findAlreadyExportedClaimIds(List.of(10L))).thenReturn(List.of(10L));

        assertThatThrownBy(() -> exportService.export837p(new Export837pRequest(List.of(10L), 1L, false, null), "biller"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("have already been exported. Pass allowRebill=true to force re-export.");
    }

    @Test
    @DisplayName("Export succeeds and does not modify claim financial totals")
    void exportCreatesBatchWithoutFinancialChanges() {
        when(claimRepository.findWithReferencesById(10L)).thenReturn(Optional.of(validClaim));
        when(payerRepository.findById(1L)).thenReturn(Optional.of(payer));
        when(batchClaimRepository.findAlreadyExportedClaimIds(List.of(10L))).thenReturn(List.of());
        when(batchRepository.countTotalBatches()).thenReturn(0L);

        when(generator.generate(any(), any(), eq(1L), any())).thenReturn(
                new Synthetic837pGenerator.GeneratorResult("ISA*00*...~", 15, "000000001", "1", "0001")
        );

        when(batchRepository.save(any(EdiExportBatch.class))).thenAnswer(inv -> {
            EdiExportBatch b = inv.getArgument(0);
            b.setId(100L);
            return b;
        });

        EdiExportBatchDetailResponse response = exportService.export837p(
                new Export837pRequest(List.of(10L), 1L, false, "Initial export"),
                "biller"
        );

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.claimCount()).isEqualTo(1);
        assertThat(response.totalBilledAmount()).isEqualByComparingTo("150.00");
        assertThat(response.exportStatus()).isEqualTo("EXPORTED");

        // Verify claim state was NOT mutated
        assertThat(validClaim.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(validClaim.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}

