package com.claire.claims.service;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.domain.DataImportBatch;
import com.claire.claims.domain.Patient;
import com.claire.claims.dto.BulkImportDtos.CsvImportResultResponse;
import com.claire.claims.dto.BulkImportDtos.CsvPreviewResponse;
import com.claire.claims.dto.BulkImportDtos.ExecuteImportRequest;
import com.claire.claims.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkImportServiceTest {

    @Mock
    private PatientRepository patients;

    @Mock
    private PayerRepository payers;

    @Mock
    private ProviderRepository providers;

    @Mock
    private InsurancePolicyRepository policies;

    @Mock
    private DataImportBatchRepository batches;

    private BulkImportService importService;

    @BeforeEach
    void setUp() {
        importService = new BulkImportService(patients, payers, providers, policies, batches);
    }

    @Test
    @DisplayName("getTemplateCsv generates valid CSV templates with headers")
    void testGetTemplateCsv() {
        byte[] ptCsv = importService.getTemplateCsv("PATIENTS");
        String ptStr = new String(ptCsv, StandardCharsets.UTF_8);
        assertThat(ptStr).contains("mrn,firstName,lastName,dateOfBirth");

        byte[] prvCsv = importService.getTemplateCsv("PROVIDERS");
        String prvStr = new String(prvCsv, StandardCharsets.UTF_8);
        assertThat(prvStr).contains("npi,firstName,lastName,specialty");
    }

    @Test
    @DisplayName("previewAndValidate detects valid patient rows and flags duplicate MRNs")
    void testPreviewPatients() {
        String csv = """
                mrn,firstName,lastName,dateOfBirth,phone,email,addressLine1,city,state,postalCode
                MRN-88001,John,Doe,1990-01-01,617-555-0100,john@example.com,1 Main St,Boston,MA,02115
                MRN-88002,Jane,Smith,1992-05-15,617-555-0200,jane@example.com,2 Main St,Boston,MA,02115
                """;

        when(patients.existsByMrn("MRN-88001")).thenReturn(false);
        when(patients.existsByMrn("MRN-88002")).thenReturn(true); // already in DB

        ByteArrayInputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        CsvPreviewResponse preview = importService.previewAndValidate("PATIENTS", in, "patients.csv");

        assertThat(preview.totalRows()).isEqualTo(2);
        assertThat(preview.validRows()).isEqualTo(1);
        assertThat(preview.errorRows()).isEqualTo(1);
        assertThat(preview.duplicateRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("previewAndValidate flags invalid NPI format for providers")
    void testPreviewProvidersInvalidNpi() {
        String csv = """
                npi,firstName,lastName,specialty,taxId
                12345,Gregory,House,Diagnostics,12-3456789
                """;

        ByteArrayInputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        CsvPreviewResponse preview = importService.previewAndValidate("PROVIDERS", in, "providers.csv");

        assertThat(preview.validRows()).isEqualTo(0);
        assertThat(preview.errorRows()).isEqualTo(1);
        assertThat(preview.rows().get(0).errors()).anyMatch(e -> e.contains("exactly 10 digits"));
    }

    @Test
    @DisplayName("executeImport successfully inserts records and records audit batch")
    void testExecuteImportSuccess() {
        Map<String, String> row = Map.of(
                "mrn", "MRN-77001",
                "firstName", "Alice",
                "lastName", "Wong",
                "dateOfBirth", "1988-03-20",
                "phone", "617-555-0300",
                "email", "alice@example.com",
                "addressLine1", "10 Elm St",
                "city", "Boston",
                "state", "MA",
                "postalCode", "02115"
        );

        when(patients.existsByMrn("MRN-77001")).thenReturn(false);
        when(batches.save(any(DataImportBatch.class))).thenAnswer(inv -> {
            DataImportBatch b = inv.getArgument(0);
            b.setId(100L);
            return b;
        });

        ExecuteImportRequest req = new ExecuteImportRequest("patients.csv", List.of(row), true);
        CsvImportResultResponse res = importService.executeImport("PATIENTS", req, "admin");

        assertThat(res.batchId()).isEqualTo(100L);
        assertThat(res.successCount()).isEqualTo(1);
        assertThat(res.errorCount()).isEqualTo(0);
        assertThat(res.status()).isEqualTo("COMPLETED");

        verify(patients, times(1)).save(any(Patient.class));
    }

    @Test
    @DisplayName("executeImport with abortOnError=true throws BusinessRuleException on validation failure")
    void testExecuteImportAbortOnError() {
        Map<String, String> row = Map.of(
                "mrn", "", // invalid blank MRN
                "firstName", "Alice",
                "lastName", "Wong",
                "dateOfBirth", "1988-03-20"
        );

        when(batches.save(any(DataImportBatch.class))).thenAnswer(inv -> inv.getArgument(0));

        ExecuteImportRequest req = new ExecuteImportRequest("patients.csv", List.of(row), true);

        assertThatThrownBy(() -> importService.executeImport("PATIENTS", req, "admin"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("MRN is required");
    }
}

