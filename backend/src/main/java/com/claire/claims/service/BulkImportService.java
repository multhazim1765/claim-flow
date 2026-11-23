package com.claire.claims.service;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.domain.*;
import com.claire.claims.dto.BulkImportDtos.*;
import com.claire.claims.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class BulkImportService {

    private final PatientRepository patients;
    private final PayerRepository payers;
    private final ProviderRepository providers;
    private final InsurancePolicyRepository policies;
    private final DataImportBatchRepository batches;

    public BulkImportService(PatientRepository patients,
                             PayerRepository payers,
                             ProviderRepository providers,
                             InsurancePolicyRepository policies,
                             DataImportBatchRepository batches) {
        this.patients = patients;
        this.payers = payers;
        this.providers = providers;
        this.policies = policies;
        this.batches = batches;
    }

    public byte[] getTemplateCsv(String entityType) {
        String type = entityType.toUpperCase().trim();
        StringBuilder sb = new StringBuilder();
        switch (type) {
            case "PATIENTS" -> {
                sb.append("mrn,firstName,lastName,dateOfBirth,phone,email,addressLine1,city,state,postalCode\n");
                sb.append("MRN-90001,Lucas,Vance,1985-04-12,617-555-0199,lucas.vance@example.com,742 Evergreen Terr,Boston,MA,02115\n");
                sb.append("MRN-90002,Elena,Reyes,1992-09-28,617-555-0188,elena.reyes@example.com,100 Cambridge St,Cambridge,MA,02138\n");
            }
            case "PROVIDERS" -> {
                sb.append("npi,firstName,lastName,specialty,taxId\n");
                sb.append("1992837465,Marcus,Welby,Family Medicine,12-3456789\n");
                sb.append("1883746592,Allison,Cameron,Immunology,98-7654321\n");
            }
            case "PAYERS" -> {
                sb.append("payerCode,name,planType,claimsAddress,phone\n");
                sb.append("PAY-HARVARD,Harvard Pilgrim Health,COMMERCIAL,PO Box 542 Boston MA,800-555-1212\n");
                sb.append("PAY-TUFTS,Tufts Health Plan,COMMERCIAL,PO Box 789 Watertown MA,800-555-3434\n");
            }
            case "POLICIES" -> {
                sb.append("patientMrn,payerCode,memberId,groupNumber,priority,effectiveDate,terminationDate\n");
                sb.append("MRN-000001,PAY-BCBS,MBR-998877,GRP-4400,PRIMARY,2026-01-01,2026-12-31\n");
                sb.append("MRN-000002,PAY-AETNA,MBR-665544,GRP-8800,PRIMARY,2026-01-01,\n");
            }
            default -> throw new BusinessRuleException("Unsupported entity type: " + entityType);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public CsvPreviewResponse previewAndValidate(String entityType, InputStream csvStream, String filename) {
        String type = entityType.toUpperCase().trim();
        List<List<String>> rawRows = parseCsv(csvStream);
        if (rawRows.isEmpty()) {
            throw new BusinessRuleException("The uploaded CSV file is empty.");
        }

        List<String> headers = rawRows.get(0).stream().map(String::trim).toList();
        List<String> expected = getExpectedHeaders(type);
        for (String exp : expected) {
            if (headers.stream().noneMatch(h -> h.equalsIgnoreCase(exp))) {
                throw new BusinessRuleException("Missing required column in CSV: " + exp);
            }
        }

        List<CsvRowValidation> validatedRows = new ArrayList<>();
        Set<String> seenIdentifiers = new HashSet<>();
        int validCount = 0;
        int errorCount = 0;
        int duplicateCount = 0;

        for (int i = 1; i < rawRows.size(); i++) {
            List<String> rowValues = rawRows.get(i);
            Map<String, String> rowMap = new LinkedHashMap<>();
            for (int h = 0; h < headers.size(); h++) {
                String val = h < rowValues.size() ? rowValues.get(h).trim() : "";
                rowMap.put(headers.get(h), val);
            }

            List<String> errors = new ArrayList<>();
            boolean isDuplicate = validateRow(type, rowMap, seenIdentifiers, errors);

            boolean isValid = errors.isEmpty();
            if (isValid) {
                validCount++;
            } else {
                errorCount++;
            }
            if (isDuplicate) {
                duplicateCount++;
            }

            validatedRows.add(new CsvRowValidation(i, rowMap, errors, isValid, isDuplicate));
        }

        return new CsvPreviewResponse(type, validatedRows.size(), validCount, errorCount, duplicateCount, headers, validatedRows);
    }

    @Transactional
    public CsvImportResultResponse executeImport(String entityType, ExecuteImportRequest request, String currentUser) {
        String type = entityType.toUpperCase().trim();
        List<Map<String, String>> rows = request.rows();
        if (rows == null || rows.isEmpty()) {
            throw new BusinessRuleException("No rows provided for import.");
        }

        List<String> allErrors = new ArrayList<>();
        int successCount = 0;
        int errorCount = 0;
        Set<String> seenInBatch = new HashSet<>();

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            List<String> rowErrors = new ArrayList<>();
            validateRow(type, row, seenInBatch, rowErrors);

            if (!rowErrors.isEmpty()) {
                errorCount++;
                allErrors.add("Row " + (i + 1) + ": " + String.join("; ", rowErrors));
                if (request.abortOnError()) {
                    break;
                }
            } else {
                try {
                    saveEntity(type, row);
                    successCount++;
                } catch (Exception ex) {
                    errorCount++;
                    allErrors.add("Row " + (i + 1) + " save failure: " + ex.getMessage());
                    if (request.abortOnError()) {
                        break;
                    }
                }
            }
        }

        if (request.abortOnError() && errorCount > 0) {
            DataImportBatch batch = new DataImportBatch();
            batch.setEntityType(type);
            batch.setFilename(request.filename() != null ? request.filename() : "import.csv");
            batch.setTotalRows(rows.size());
            batch.setSuccessCount(0);
            batch.setErrorCount(errorCount);
            batch.setStatus("FAILED");
            batch.setImportedBy(currentUser);
            batch.setErrorSummary(String.join("\n", allErrors));
            batches.save(batch);

            throw new BusinessRuleException("Import aborted due to validation errors:\n" + String.join("\n", allErrors));
        }

        DataImportBatch batch = new DataImportBatch();
        batch.setEntityType(type);
        batch.setFilename(request.filename() != null ? request.filename() : "import.csv");
        batch.setTotalRows(rows.size());
        batch.setSuccessCount(successCount);
        batch.setErrorCount(errorCount);
        batch.setStatus(errorCount == 0 ? "COMPLETED" : (successCount > 0 ? "PARTIAL" : "FAILED"));
        batch.setImportedBy(currentUser);
        batch.setErrorSummary(allErrors.isEmpty() ? null : String.join("\n", allErrors));
        DataImportBatch savedBatch = batches.save(batch);

        return new CsvImportResultResponse(
                savedBatch.getId(),
                type,
                rows.size(),
                successCount,
                errorCount,
                savedBatch.getStatus(),
                allErrors);
    }

    @Transactional(readOnly = true)
    public Page<ImportBatchSummaryResponse> listBatches(String entityType, Pageable pageable) {
        Page<DataImportBatch> page = batches.findByEntityTypeOrderByCreatedAtDesc(entityType.toUpperCase().trim(), pageable);
        return page.map(b -> new ImportBatchSummaryResponse(
                b.getId(),
                b.getEntityType(),
                b.getFilename(),
                b.getTotalRows(),
                b.getSuccessCount(),
                b.getErrorCount(),
                b.getStatus(),
                b.getImportedBy(),
                b.getCreatedAt(),
                b.getErrorSummary()));
    }

    private boolean validateRow(String entityType, Map<String, String> row, Set<String> seen, List<String> errors) {
        boolean duplicate = false;
        switch (entityType) {
            case "PATIENTS" -> {
                String mrn = getCaseInsensitive(row, "mrn");
                String firstName = getCaseInsensitive(row, "firstName");
                String lastName = getCaseInsensitive(row, "lastName");
                String dob = getCaseInsensitive(row, "dateOfBirth");
                String email = getCaseInsensitive(row, "email");
                String state = getCaseInsensitive(row, "state");
                String zip = getCaseInsensitive(row, "postalCode");

                if (mrn == null || mrn.isBlank()) errors.add("MRN is required");
                else if (mrn.length() > 30) errors.add("MRN cannot exceed 30 characters");
                else {
                    if (patients.existsByMrn(mrn) || !seen.add(mrn.toUpperCase())) {
                        errors.add("MRN '" + mrn + "' already exists");
                        duplicate = true;
                    }
                }

                if (firstName == null || firstName.isBlank()) errors.add("First name is required");
                if (lastName == null || lastName.isBlank()) errors.add("Last name is required");
                if (dob == null || dob.isBlank()) errors.add("Date of birth is required");
                else {
                    try {
                        LocalDate d = LocalDate.parse(dob);
                        if (d.isAfter(LocalDate.now())) errors.add("Date of birth cannot be in the future");
                    } catch (DateTimeParseException ex) {
                        errors.add("Date of birth must be in YYYY-MM-DD format");
                    }
                }
                if (email != null && !email.isBlank() && !email.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
                    errors.add("Invalid email format");
                }
                if (state != null && !state.isBlank() && !state.matches("^[A-Z]{2}$")) {
                    errors.add("State must be a 2-letter uppercase postal abbreviation");
                }
                if (zip != null && !zip.isBlank() && !zip.matches("^\\d{5}(-\\d{4})?$")) {
                    errors.add("Postal code must be 5 digits (e.g. 02115 or 02115-1234)");
                }
            }
            case "PROVIDERS" -> {
                String npi = getCaseInsensitive(row, "npi");
                String firstName = getCaseInsensitive(row, "firstName");
                String lastName = getCaseInsensitive(row, "lastName");

                if (npi == null || npi.isBlank()) errors.add("NPI is required");
                else if (!npi.matches("^\\d{10}$")) errors.add("NPI must be exactly 10 digits");
                else {
                    if (providers.existsByNpi(npi) || !seen.add(npi)) {
                        errors.add("NPI '" + npi + "' already exists");
                        duplicate = true;
                    }
                }
                if (firstName == null || firstName.isBlank()) errors.add("First name is required");
                if (lastName == null || lastName.isBlank()) errors.add("Last name is required");
            }
            case "PAYERS" -> {
                String code = getCaseInsensitive(row, "payerCode");
                String name = getCaseInsensitive(row, "name");
                String planType = getCaseInsensitive(row, "planType");

                if (code == null || code.isBlank()) errors.add("Payer code is required");
                else if (code.length() > 20) errors.add("Payer code cannot exceed 20 characters");
                else {
                    if (payers.existsByPayerCode(code) || !seen.add(code.toUpperCase())) {
                        errors.add("Payer code '" + code + "' already exists");
                        duplicate = true;
                    }
                }
                if (name == null || name.isBlank()) errors.add("Payer name is required");
                if (planType == null || planType.isBlank()) errors.add("Plan type is required");
                else {
                    try {
                        PlanType.valueOf(planType.toUpperCase());
                    } catch (IllegalArgumentException ex) {
                        errors.add("Invalid plan type. Must be COMMERCIAL, MEDICARE, MEDICAID, TRICARE, WORKERS_COMP, or SELF_PAY");
                    }
                }
            }
            case "POLICIES" -> {
                String mrn = getCaseInsensitive(row, "patientMrn");
                String payerCode = getCaseInsensitive(row, "payerCode");
                String memberId = getCaseInsensitive(row, "memberId");
                String priority = getCaseInsensitive(row, "priority");
                String effDate = getCaseInsensitive(row, "effectiveDate");
                String termDate = getCaseInsensitive(row, "terminationDate");

                if (mrn == null || mrn.isBlank()) errors.add("Patient MRN is required");
                else if (!patients.existsByMrn(mrn)) errors.add("Patient MRN '" + mrn + "' does not exist");

                if (payerCode == null || payerCode.isBlank()) errors.add("Payer code is required");
                else if (!payers.existsByPayerCode(payerCode)) errors.add("Payer code '" + payerCode + "' does not exist");

                if (memberId == null || memberId.isBlank()) errors.add("Member ID is required");
                if (priority == null || priority.isBlank()) errors.add("Policy priority is required");
                else {
                    try {
                        PolicyPriority.valueOf(priority.toUpperCase());
                    } catch (IllegalArgumentException ex) {
                        errors.add("Priority must be PRIMARY, SECONDARY, or TERTIARY");
                    }
                }

                LocalDate eff = null;
                if (effDate == null || effDate.isBlank()) errors.add("Effective date is required");
                else {
                    try {
                        eff = LocalDate.parse(effDate);
                    } catch (DateTimeParseException ex) {
                        errors.add("Effective date must be YYYY-MM-DD");
                    }
                }

                if (termDate != null && !termDate.isBlank()) {
                    try {
                        LocalDate term = LocalDate.parse(termDate);
                        if (eff != null && term.isBefore(eff)) {
                            errors.add("Termination date cannot be before effective date");
                        }
                    } catch (DateTimeParseException ex) {
                        errors.add("Termination date must be YYYY-MM-DD");
                    }
                }
            }
        }
        return duplicate;
    }

    private void saveEntity(String entityType, Map<String, String> row) {
        switch (entityType) {
            case "PATIENTS" -> {
                Patient p = new Patient();
                p.setMrn(getCaseInsensitive(row, "mrn"));
                p.setFirstName(getCaseInsensitive(row, "firstName"));
                p.setLastName(getCaseInsensitive(row, "lastName"));
                p.setDateOfBirth(LocalDate.parse(getCaseInsensitive(row, "dateOfBirth")));
                p.setPhone(getCaseInsensitive(row, "phone"));
                p.setEmail(getCaseInsensitive(row, "email"));
                p.setAddressLine1(getCaseInsensitive(row, "addressLine1"));
                p.setCity(getCaseInsensitive(row, "city"));
                p.setState(getCaseInsensitive(row, "state"));
                p.setPostalCode(getCaseInsensitive(row, "postalCode"));
                p.setActive(true);
                patients.save(p);
            }
            case "PROVIDERS" -> {
                Provider pr = new Provider();
                pr.setNpi(getCaseInsensitive(row, "npi"));
                pr.setFirstName(getCaseInsensitive(row, "firstName"));
                pr.setLastName(getCaseInsensitive(row, "lastName"));
                pr.setSpecialty(getCaseInsensitive(row, "specialty"));
                pr.setTaxId(getCaseInsensitive(row, "taxId"));
                pr.setActive(true);
                providers.save(pr);
            }
            case "PAYERS" -> {
                Payer pay = new Payer();
                pay.setPayerCode(getCaseInsensitive(row, "payerCode"));
                pay.setName(getCaseInsensitive(row, "name"));
                pay.setPlanType(PlanType.valueOf(getCaseInsensitive(row, "planType").toUpperCase()));
                pay.setClaimsAddress(getCaseInsensitive(row, "claimsAddress"));
                pay.setPhone(getCaseInsensitive(row, "phone"));
                pay.setActive(true);
                payers.save(pay);
            }
            case "POLICIES" -> {
                String mrn = getCaseInsensitive(row, "patientMrn");
                String payerCode = getCaseInsensitive(row, "payerCode");
                Patient pat = patients.findByMrn(mrn).orElseThrow();
                Payer payer = payers.findByPayerCode(payerCode).orElseThrow();

                InsurancePolicy pol = new InsurancePolicy();
                pol.setPatient(pat);
                pol.setPayer(payer);
                pol.setMemberId(getCaseInsensitive(row, "memberId"));
                pol.setGroupNumber(getCaseInsensitive(row, "groupNumber"));
                pol.setPriority(PolicyPriority.valueOf(getCaseInsensitive(row, "priority").toUpperCase()));
                pol.setEffectiveDate(LocalDate.parse(getCaseInsensitive(row, "effectiveDate")));
                String term = getCaseInsensitive(row, "terminationDate");
                if (term != null && !term.isBlank()) {
                    pol.setTerminationDate(LocalDate.parse(term));
                }
                pol.setActive(true);
                policies.save(pol);
            }
        }
    }

    private List<String> getExpectedHeaders(String entityType) {
        return switch (entityType) {
            case "PATIENTS" -> List.of("mrn", "firstName", "lastName", "dateOfBirth");
            case "PROVIDERS" -> List.of("npi", "firstName", "lastName");
            case "PAYERS" -> List.of("payerCode", "name", "planType");
            case "POLICIES" -> List.of("patientMrn", "payerCode", "memberId", "priority", "effectiveDate");
            default -> List.of();
        };
    }

    private String getCaseInsensitive(Map<String, String> map, String key) {
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }

    private List<List<String>> parseCsv(InputStream in) {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            StringBuilder currentField = new StringBuilder();
            List<String> currentRow = new ArrayList<>();
            boolean inQuotes = false;

            while ((line = reader.readLine()) != null) {
                for (int i = 0; i < line.length(); i++) {
                    char c = line.charAt(i);
                    if (c == '"') {
                        if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                            currentField.append('"');
                            i++; // Skip escaped quote
                        } else {
                            inQuotes = !inQuotes;
                        }
                    } else if (c == ',' && !inQuotes) {
                        currentRow.add(currentField.toString());
                        currentField.setLength(0);
                    } else {
                        currentField.append(c);
                    }
                }
                if (!inQuotes) {
                    currentRow.add(currentField.toString());
                    currentField.setLength(0);
                    if (!currentRow.isEmpty() && currentRow.stream().anyMatch(s -> !s.isBlank())) {
                        rows.add(new ArrayList<>(currentRow));
                    }
                    currentRow.clear();
                } else {
                    currentField.append("\n");
                }
            }
        } catch (Exception e) {
            throw new BusinessRuleException("Failed to parse CSV: " + e.getMessage());
        }
        return rows;
    }
}

