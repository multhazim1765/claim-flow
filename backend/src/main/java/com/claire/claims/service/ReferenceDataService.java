package com.claire.claims.service;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.common.ApiExceptions.ConflictException;
import com.claire.claims.common.ApiExceptions.NotFoundException;
import com.claire.claims.domain.*;
import com.claire.claims.dto.ClaimDtos.PageResponse;
import com.claire.claims.dto.ReferenceDtos.*;
import com.claire.claims.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** CRUD for patients, payers, providers and coverage. */
@Service
public class ReferenceDataService {

    private final PatientRepository patients;
    private final PayerRepository payers;
    private final ProviderRepository providers;
    private final InsurancePolicyRepository policies;
    private final ClaimMapper mapper;

    public ReferenceDataService(PatientRepository patients, PayerRepository payers,
                                ProviderRepository providers, InsurancePolicyRepository policies,
                                ClaimMapper mapper) {
        this.patients = patients;
        this.payers = payers;
        this.providers = providers;
        this.policies = policies;
        this.mapper = mapper;
    }

    // ----- patients -------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<PatientSummary> searchPatients(String q, Pageable pageable) {
        return searchPatients(q, null, pageable);
    }

    @Transactional(readOnly = true)
    public PageResponse<PatientSummary> searchPatients(String q, Boolean active, Pageable pageable) {
        String term = (q != null && !q.isBlank()) ? q.trim() : null;
        Page<Patient> page;
        if (term == null) {
            page = (active == null) ? patients.findAll(pageable) : patients.findByActive(active, pageable);
        } else {
            page = patients.searchPatients(term, active, pageable);
        }
        return new PageResponse<>(page.getContent().stream().map(mapper::toPatientSummary).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isLast());
    }

    @Transactional(readOnly = true)
    public PatientResponse getPatient(Long id) {
        Patient p = patients.findById(id).orElseThrow(() -> new NotFoundException("Patient", id));
        return mapper.toPatient(p, policies.findByPatientIdOrderByPriorityAsc(id));
    }

    @Transactional(readOnly = true)
    public List<PolicyResponse> policiesForPatient(Long patientId) {
        if (!patients.existsById(patientId)) {
            throw new NotFoundException("Patient", patientId);
        }
        return policies.findByPatientIdOrderByPriorityAsc(patientId)
                .stream().map(mapper::toPolicy).toList();
    }

    @Transactional
    public PatientResponse createPatient(PatientRequest r) {
        if (patients.existsByMrn(r.mrn())) {
            throw new ConflictException("A patient with MRN " + r.mrn() + " already exists");
        }
        Patient p = new Patient();
        copy(r, p);
        if (r.active() != null) p.setActive(r.active());
        Patient saved = patients.save(p);
        return mapper.toPatient(saved, List.of());
    }

    @Transactional
    public PatientResponse updatePatient(Long id, PatientRequest r) {
        Patient p = patients.findById(id).orElseThrow(() -> new NotFoundException("Patient", id));
        if (!p.getMrn().equals(r.mrn()) && patients.existsByMrn(r.mrn())) {
            throw new ConflictException("A patient with MRN " + r.mrn() + " already exists");
        }
        copy(r, p);
        if (r.active() != null) p.setActive(r.active());
        Patient saved = patients.save(p);
        return mapper.toPatient(saved, policies.findByPatientIdOrderByPriorityAsc(id));
    }

    @Transactional
    public PatientResponse setPatientStatus(Long id, boolean active) {
        Patient p = patients.findById(id).orElseThrow(() -> new NotFoundException("Patient", id));
        p.setActive(active);
        Patient saved = patients.save(p);
        return mapper.toPatient(saved, policies.findByPatientIdOrderByPriorityAsc(id));
    }

    private void copy(PatientRequest r, Patient p) {
        p.setMrn(r.mrn());
        p.setFirstName(r.firstName());
        p.setLastName(r.lastName());
        p.setDateOfBirth(r.dateOfBirth());
        p.setPhone(r.phone());
        p.setEmail(r.email());
        p.setAddressLine1(r.addressLine1());
        p.setCity(r.city());
        p.setState(r.state());
        p.setPostalCode(r.postalCode());
    }

    // ----- payers ---------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PayerResponse> listPayers() {
        return payers.findAll().stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(mapper::toPayer).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PayerResponse> searchPayers(String q, Boolean active, Pageable pageable) {
        String term = (q != null && !q.isBlank()) ? q.trim() : null;
        Page<Payer> page;
        if (term == null) {
            page = (active == null) ? payers.findAll(pageable) : payers.findByActive(active, pageable);
        } else {
            page = payers.searchPayers(term, active, pageable);
        }
        return new PageResponse<>(page.getContent().stream().map(mapper::toPayer).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isLast());
    }

    @Transactional(readOnly = true)
    public PayerResponse getPayer(Long id) {
        return mapper.toPayer(payers.findById(id).orElseThrow(() -> new NotFoundException("Payer", id)));
    }

    @Transactional
    public PayerResponse createPayer(PayerRequest r) {
        if (payers.existsByPayerCode(r.payerCode())) {
            throw new ConflictException("Payer code " + r.payerCode() + " is already in use");
        }
        Payer p = new Payer();
        copy(r, p);
        return mapper.toPayer(payers.save(p));
    }

    @Transactional
    public PayerResponse updatePayer(Long id, PayerRequest r) {
        Payer p = payers.findById(id).orElseThrow(() -> new NotFoundException("Payer", id));
        if (!p.getPayerCode().equals(r.payerCode()) && payers.existsByPayerCode(r.payerCode())) {
            throw new ConflictException("Payer code " + r.payerCode() + " is already in use");
        }
        copy(r, p);
        return mapper.toPayer(payers.save(p));
    }

    @Transactional
    public PayerResponse setPayerStatus(Long id, boolean active) {
        Payer p = payers.findById(id).orElseThrow(() -> new NotFoundException("Payer", id));
        p.setActive(active);
        return mapper.toPayer(payers.save(p));
    }

    private void copy(PayerRequest r, Payer p) {
        p.setPayerCode(r.payerCode());
        p.setName(r.name());
        p.setPlanType(r.planType());
        p.setClaimsAddress(r.claimsAddress());
        p.setPhone(r.phone());
        if (r.active() != null) p.setActive(r.active());
    }

    // ----- providers ------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ProviderResponse> listProviders() {
        return providers.findAll().stream()
                .sorted((a, b) -> a.getLastName().compareToIgnoreCase(b.getLastName()))
                .map(mapper::toProvider).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ProviderResponse> searchProviders(String q, Boolean active, Pageable pageable) {
        String term = (q != null && !q.isBlank()) ? q.trim() : null;
        Page<Provider> page;
        if (term == null) {
            page = (active == null) ? providers.findAll(pageable) : providers.findByActive(active, pageable);
        } else {
            page = providers.searchProviders(term, active, pageable);
        }
        return new PageResponse<>(page.getContent().stream().map(mapper::toProvider).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isLast());
    }

    @Transactional(readOnly = true)
    public ProviderResponse getProvider(Long id) {
        return mapper.toProvider(providers.findById(id)
                .orElseThrow(() -> new NotFoundException("Provider", id)));
    }

    @Transactional
    public ProviderResponse createProvider(ProviderRequest r) {
        if (providers.existsByNpi(r.npi())) {
            throw new ConflictException("NPI " + r.npi() + " is already registered");
        }
        Provider p = new Provider();
        copy(r, p);
        return mapper.toProvider(providers.save(p));
    }

    @Transactional
    public ProviderResponse updateProvider(Long id, ProviderRequest r) {
        Provider p = providers.findById(id).orElseThrow(() -> new NotFoundException("Provider", id));
        if (!p.getNpi().equals(r.npi()) && providers.existsByNpi(r.npi())) {
            throw new ConflictException("NPI " + r.npi() + " is already registered");
        }
        copy(r, p);
        return mapper.toProvider(providers.save(p));
    }

    @Transactional
    public ProviderResponse setProviderStatus(Long id, boolean active) {
        Provider p = providers.findById(id).orElseThrow(() -> new NotFoundException("Provider", id));
        p.setActive(active);
        return mapper.toProvider(providers.save(p));
    }

    private void copy(ProviderRequest r, Provider p) {
        p.setNpi(r.npi());
        p.setFirstName(r.firstName());
        p.setLastName(r.lastName());
        p.setSpecialty(r.specialty());
        p.setTaxId(r.taxId());
        if (r.active() != null) p.setActive(r.active());
    }

    // ----- policies -------------------------------------------------------

    @Transactional
    public PolicyResponse createPolicy(PolicyRequest r) {
        validatePolicyDates(r);
        validateDemoBenefits(r);
        InsurancePolicy pol = new InsurancePolicy();
        copy(r, pol);
        if (r.active() != null) pol.setActive(r.active());
        return mapper.toPolicy(policies.save(pol));
    }

    @Transactional
    public PolicyResponse updatePolicy(Long id, PolicyRequest r) {
        InsurancePolicy pol = policies.findById(id)
                .orElseThrow(() -> new NotFoundException("InsurancePolicy", id));
        validatePolicyDates(r);
        validateDemoBenefits(r);
        copy(r, pol);
        if (r.active() != null) pol.setActive(r.active());
        return mapper.toPolicy(policies.save(pol));
    }

    @Transactional
    public PolicyResponse setPolicyStatus(Long id, boolean active) {
        InsurancePolicy pol = policies.findById(id)
                .orElseThrow(() -> new NotFoundException("InsurancePolicy", id));
        pol.setActive(active);
        return mapper.toPolicy(policies.save(pol));
    }

    private void validatePolicyDates(PolicyRequest r) {
        if (r.terminationDate() != null && r.terminationDate().isBefore(r.effectiveDate())) {
            throw new BusinessRuleException("terminationDate cannot be before effectiveDate");
        }
    }

    private void validateDemoBenefits(PolicyRequest r) {
        boolean anyProvided = r.demoCopay() != null
                || r.demoCoinsurancePercent() != null
                || r.demoDeductibleTotal() != null
                || r.demoDeductibleRemaining() != null
                || r.demoOutOfPocketRemaining() != null;

        if (!anyProvided) {
            return;
        }

        boolean allProvided = r.demoCopay() != null
                && r.demoCoinsurancePercent() != null
                && r.demoDeductibleTotal() != null
                && r.demoDeductibleRemaining() != null
                && r.demoOutOfPocketRemaining() != null;

        if (!allProvided) {
            throw new BusinessRuleException("Incomplete demo benefit profile: demoCopay, demoCoinsurancePercent, demoDeductibleTotal, demoDeductibleRemaining, and demoOutOfPocketRemaining must all be provided or all omitted.");
        }

        if (r.demoCopay().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("demoCopay must be non-negative");
        }
        if (r.demoCoinsurancePercent() < 0 || r.demoCoinsurancePercent() > 100) {
            throw new BusinessRuleException("demoCoinsurancePercent must be between 0 and 100");
        }
        if (r.demoDeductibleTotal().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("demoDeductibleTotal must be non-negative");
        }
        if (r.demoDeductibleRemaining().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("demoDeductibleRemaining must be non-negative");
        }
        if (r.demoOutOfPocketRemaining().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("demoOutOfPocketRemaining must be non-negative");
        }
        if (r.demoDeductibleRemaining().compareTo(r.demoDeductibleTotal()) > 0) {
            throw new BusinessRuleException("Remaining deductible (" + r.demoDeductibleRemaining() + ") cannot exceed total deductible (" + r.demoDeductibleTotal() + ")");
        }
    }

    private void copy(PolicyRequest r, InsurancePolicy pol) {
        Patient patient = patients.findById(r.patientId())
                .orElseThrow(() -> new NotFoundException("Patient", r.patientId()));
        Payer payer = payers.findById(r.payerId())
                .orElseThrow(() -> new NotFoundException("Payer", r.payerId()));
        pol.setPatient(patient);
        pol.setPayer(payer);
        pol.setMemberId(r.memberId());
        pol.setGroupNumber(r.groupNumber());
        pol.setPriority(r.priority());
        pol.setEffectiveDate(r.effectiveDate());
        pol.setTerminationDate(r.terminationDate());
        pol.setDemoCopay(r.demoCopay());
        pol.setDemoCoinsurancePercent(r.demoCoinsurancePercent());
        pol.setDemoDeductibleTotal(r.demoDeductibleTotal());
        pol.setDemoDeductibleRemaining(r.demoDeductibleRemaining());
        pol.setDemoOutOfPocketRemaining(r.demoOutOfPocketRemaining());
    }
}
