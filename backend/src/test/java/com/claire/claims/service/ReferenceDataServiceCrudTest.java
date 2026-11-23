package com.claire.claims.service;

import com.claire.claims.common.ApiExceptions.ConflictException;
import com.claire.claims.domain.Patient;
import com.claire.claims.domain.Payer;
import com.claire.claims.domain.PlanType;
import com.claire.claims.domain.Provider;
import com.claire.claims.dto.ClaimDtos.PageResponse;
import com.claire.claims.dto.ReferenceDtos.*;
import com.claire.claims.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReferenceDataServiceCrudTest {

    @Mock
    private PatientRepository patients;

    @Mock
    private PayerRepository payers;

    @Mock
    private ProviderRepository providers;

    @Mock
    private InsurancePolicyRepository policies;

    private ClaimMapper mapper;
    private ReferenceDataService service;

    @BeforeEach
    void setUp() {
        mapper = new ClaimMapper();
        service = new ReferenceDataService(patients, payers, providers, policies, mapper);
    }

    @Test
    @DisplayName("searchPatients with active filter queries patientRepository correctly")
    void testSearchPatientsWithActiveFilter() {
        Patient p = new Patient();
        p.setId(1L);
        p.setMrn("MRN-001");
        p.setFirstName("John");
        p.setLastName("Doe");
        p.setDateOfBirth(LocalDate.of(1980, 1, 1));
        p.setActive(true);

        Pageable pageable = PageRequest.of(0, 20);
        when(patients.searchPatients(eq("John"), eq(true), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(p), pageable, 1));

        PageResponse<PatientSummary> res = service.searchPatients("John", true, pageable);
        assertThat(res.content()).hasSize(1);
        assertThat(res.content().get(0).mrn()).isEqualTo("MRN-001");
        assertThat(res.content().get(0).active()).isTrue();
    }

    @Test
    @DisplayName("createPatient throws ConflictException when MRN already exists")
    void testCreatePatientConflict() {
        PatientRequest req = new PatientRequest("MRN-EXIST", "John", "Doe", LocalDate.of(1990, 1, 1),
                null, null, null, null, null, null, true);

        when(patients.existsByMrn("MRN-EXIST")).thenReturn(true);

        assertThatThrownBy(() -> service.createPatient(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("setPatientStatus toggles active flag on patient")
    void testSetPatientStatus() {
        Patient p = new Patient();
        p.setId(5L);
        p.setMrn("MRN-005");
        p.setFirstName("Sarah");
        p.setLastName("Connor");
        p.setDateOfBirth(LocalDate.of(1975, 5, 5));
        p.setActive(true);

        when(patients.findById(5L)).thenReturn(Optional.of(p));
        when(patients.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));

        PatientResponse res = service.setPatientStatus(5L, false);
        assertThat(res.active()).isFalse();
        assertThat(p.isActive()).isFalse();
    }

    @Test
    @DisplayName("searchPayers queries payerRepository with active filter")
    void testSearchPayers() {
        Payer payer = new Payer();
        payer.setId(10L);
        payer.setPayerCode("PAY-TEST");
        payer.setName("Test Payer");
        payer.setPlanType(PlanType.COMMERCIAL);
        payer.setActive(true);

        Pageable pageable = PageRequest.of(0, 10);
        when(payers.searchPayers(eq("Test"), eq(true), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(payer), pageable, 1));

        PageResponse<PayerResponse> res = service.searchPayers("Test", true, pageable);
        assertThat(res.content()).hasSize(1);
        assertThat(res.content().get(0).payerCode()).isEqualTo("PAY-TEST");
    }

    @Test
    @DisplayName("searchProviders queries providerRepository with active filter")
    void testSearchProviders() {
        Provider prov = new Provider();
        prov.setId(20L);
        prov.setNpi("1234567890");
        prov.setFirstName("Doctor");
        prov.setLastName("Who");
        prov.setActive(true);

        Pageable pageable = PageRequest.of(0, 10);
        when(providers.searchProviders(eq("Who"), eq(true), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(prov), pageable, 1));

        PageResponse<ProviderResponse> res = service.searchProviders("Who", true, pageable);
        assertThat(res.content()).hasSize(1);
        assertThat(res.content().get(0).npi()).isEqualTo("1234567890");
    }
}

