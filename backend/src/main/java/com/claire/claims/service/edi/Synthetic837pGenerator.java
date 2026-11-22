package com.claire.claims.service.edi;

import com.claire.claims.domain.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates synthetic educational ASC X12 005010X222A1 (837 Professional) claim files.
 * Educational implementation for demonstration and architectural study; not certified for live production.
 */
@Component
public class Synthetic837pGenerator {

    private static final String ELEM_SEP = "*";
    private static final String SEG_TERM = "~\n";
    private static final String COMP_SEP = ":";
    private static final String REP_SEP = "^";

    private static final DateTimeFormatter DATE_FMT_6 = DateTimeFormatter.ofPattern("yyMMdd");
    private static final DateTimeFormatter TIME_FMT_4 = DateTimeFormatter.ofPattern("HHmm");
    private static final DateTimeFormatter DATE_FMT_8 = DateTimeFormatter.ofPattern("yyyyMMdd");

    public record GeneratorResult(
            String payload,
            int segmentCount,
            String interchangeControlNumber,
            String groupControlNumber,
            String transactionControlNumber
    ) {}

    public GeneratorResult generate(
            List<Claim> claims,
            Payer payer,
            long batchId,
            OffsetDateTime exportTime
    ) {
        String icn = String.format("%09d", batchId % 1_000_000_000L);
        String gcn = String.valueOf(batchId);
        String tcn = String.format("%04d", batchId % 10_000L);

        List<String> segments = new ArrayList<>();

        // 1. ISA Header
        String submitterId = padRight("CLAIMFLOW", 15);
        String receiverId = padRight(payer != null && payer.getPayerCode() != null ? payer.getPayerCode() : "RECEIVER", 15);
        String isaDate = exportTime.format(DATE_FMT_6);
        String isaTime = exportTime.format(TIME_FMT_4);

        String isa = "ISA" + ELEM_SEP +
                "00" + ELEM_SEP + "          " + ELEM_SEP +
                "00" + ELEM_SEP + "          " + ELEM_SEP +
                "ZZ" + ELEM_SEP + submitterId + ELEM_SEP +
                "ZZ" + ELEM_SEP + receiverId + ELEM_SEP +
                isaDate + ELEM_SEP + isaTime + ELEM_SEP +
                REP_SEP + ELEM_SEP +
                "00501" + ELEM_SEP +
                icn + ELEM_SEP +
                "0" + ELEM_SEP +
                "T" + ELEM_SEP + // Test data: Educational Synthetic
                COMP_SEP;
        segments.add(isa);

        // 2. GS Header
        String gsDate = exportTime.format(DATE_FMT_8);
        String gsTime = exportTime.format(TIME_FMT_4);
        String gs = "GS" + ELEM_SEP +
                "HC" + ELEM_SEP +
                "CLAIMFLOW" + ELEM_SEP +
                (payer != null && payer.getPayerCode() != null ? sanitize(payer.getPayerCode()) : "PAYER") + ELEM_SEP +
                gsDate + ELEM_SEP +
                gsTime + ELEM_SEP +
                gcn + ELEM_SEP +
                "X" + ELEM_SEP +
                "005010X222A1";
        segments.add(gs);

        // Track start index for ST-SE segment count
        int stStartIndex = segments.size();

        // 3. ST Header
        String st = "ST" + ELEM_SEP + "837" + ELEM_SEP + tcn + ELEM_SEP + "005010X222A1";
        segments.add(st);

        // 4. BHT Beginning of Hierarchical Transaction
        String bht = "BHT" + ELEM_SEP + "0019" + ELEM_SEP + "00" + ELEM_SEP + tcn + ELEM_SEP +
                gsDate + ELEM_SEP + gsTime + ELEM_SEP + "CH";
        segments.add(bht);

        // 5. Loop 1000A Submitter Name & Contact
        segments.add("NM1" + ELEM_SEP + "41" + ELEM_SEP + "2" + ELEM_SEP + "CLAIMFLOW RCM" + ELEM_SEP + ELEM_SEP + ELEM_SEP + ELEM_SEP + ELEM_SEP + "46" + ELEM_SEP + "CLAIMFLOW");
        segments.add("PER" + ELEM_SEP + "IC" + ELEM_SEP + "EDI COORDINATOR" + ELEM_SEP + "TE" + ELEM_SEP + "8005550199" + ELEM_SEP + "EM" + ELEM_SEP + "support@claimflow.example");

        // 6. Loop 1000B Receiver Name
        String payerName = payer != null && payer.getName() != null ? sanitize(payer.getName()) : "COMMERCIAL PAYER";
        String payerCode = payer != null && payer.getPayerCode() != null ? sanitize(payer.getPayerCode()) : "PAYER001";
        segments.add("NM1" + ELEM_SEP + "40" + ELEM_SEP + "2" + ELEM_SEP + payerName + ELEM_SEP + ELEM_SEP + ELEM_SEP + ELEM_SEP + ELEM_SEP + "46" + ELEM_SEP + payerCode);

        // Hierarchical Level tracking
        int hlIndex = 1;

        for (Claim claim : claims) {
            Provider prov = claim.getProvider();
            Patient pat = claim.getPatient();
            InsurancePolicy policy = claim.getPolicy();

            int billingHl = hlIndex++;
            int subscriberHl = hlIndex++;

            // Loop 2000A: Billing Provider HL
            segments.add("HL" + ELEM_SEP + billingHl + ELEM_SEP + ELEM_SEP + "20" + ELEM_SEP + "1");

            // Loop 2010AA: Billing Provider Name & Identification
            String provLast = prov != null ? sanitize(prov.getLastName()) : "PROVIDER";
            String provFirst = prov != null ? sanitize(prov.getFirstName()) : "CLINICIAN";
            String provNpi = prov != null && prov.getNpi() != null ? prov.getNpi() : "1234567890";
            String provTaxId = prov != null && prov.getTaxId() != null ? prov.getTaxId() : "999999999";

            segments.add("NM1" + ELEM_SEP + "85" + ELEM_SEP + "1" + ELEM_SEP + provLast + ELEM_SEP + provFirst + ELEM_SEP + ELEM_SEP + ELEM_SEP + ELEM_SEP + "XX" + ELEM_SEP + provNpi);
            segments.add("N3" + ELEM_SEP + "100 MEDICAL CENTER BLVD");
            segments.add("N4" + ELEM_SEP + "BOSTON" + ELEM_SEP + "MA" + ELEM_SEP + "02115");
            segments.add("REF" + ELEM_SEP + "EI" + ELEM_SEP + sanitize(provTaxId));

            // Loop 2000B: Subscriber HL
            segments.add("HL" + ELEM_SEP + subscriberHl + ELEM_SEP + billingHl + ELEM_SEP + "22" + ELEM_SEP + "0");

            // SBR: Subscriber Information
            segments.add("SBR" + ELEM_SEP + "P" + ELEM_SEP + "18" + ELEM_SEP + ELEM_SEP + ELEM_SEP + ELEM_SEP + ELEM_SEP + ELEM_SEP + ELEM_SEP + "CI");

            // Loop 2010BA: Subscriber Name
            String patLast = pat != null ? sanitize(pat.getLastName()) : "PATIENT";
            String patFirst = pat != null ? sanitize(pat.getFirstName()) : "JOHN";
            String memberId = policy != null && policy.getMemberId() != null ? sanitize(policy.getMemberId()) : (pat != null ? pat.getMrn() : "MEM001");

            segments.add("NM1" + ELEM_SEP + "IL" + ELEM_SEP + "1" + ELEM_SEP + patLast + ELEM_SEP + patFirst + ELEM_SEP + ELEM_SEP + ELEM_SEP + ELEM_SEP + "MI" + ELEM_SEP + memberId);
            String patAddr = pat != null && pat.getAddressLine1() != null ? sanitize(pat.getAddressLine1()) : "123 HEALTH AVE";
            String patCity = pat != null && pat.getCity() != null ? sanitize(pat.getCity()) : "BOSTON";
            String patState = pat != null && pat.getState() != null ? sanitize(pat.getState()) : "MA";
            String patZip = pat != null && pat.getPostalCode() != null ? sanitize(pat.getPostalCode().replace("-", "")) : "02115";
            segments.add("N3" + ELEM_SEP + patAddr);
            segments.add("N4" + ELEM_SEP + patCity + ELEM_SEP + patState + ELEM_SEP + patZip);

            if (pat != null && pat.getDateOfBirth() != null) {
                segments.add("DMG" + ELEM_SEP + "D8" + ELEM_SEP + pat.getDateOfBirth().format(DATE_FMT_8) + ELEM_SEP + "U");
            }

            // Loop 2010BB: Payer Name
            segments.add("NM1" + ELEM_SEP + "PR" + ELEM_SEP + "2" + ELEM_SEP + payerName + ELEM_SEP + ELEM_SEP + ELEM_SEP + ELEM_SEP + ELEM_SEP + "PI" + ELEM_SEP + payerCode);
            String payerAddr = payer != null && payer.getClaimsAddress() != null ? sanitize(payer.getClaimsAddress()) : "400 PAYER PARKWAY";
            segments.add("N3" + ELEM_SEP + payerAddr);
            segments.add("N4" + ELEM_SEP + "HARTFORD" + ELEM_SEP + "CT" + ELEM_SEP + "06152");

            // Loop 2300: Claim Information
            String clmPlace = claim.getPlaceOfService() != null ? claim.getPlaceOfService() : "11";
            BigDecimal totalCharge = claim.getTotalCharge() != null ? claim.getTotalCharge() : BigDecimal.ZERO;
            segments.add("CLM" + ELEM_SEP +
                    claim.getClaimNumber() + ELEM_SEP +
                    totalCharge.toPlainString() + ELEM_SEP +
                    ELEM_SEP + ELEM_SEP +
                    clmPlace + COMP_SEP + "B" + COMP_SEP + "1" + ELEM_SEP +
                    "Y" + ELEM_SEP + "A" + ELEM_SEP + "Y" + ELEM_SEP + "Y");

            // Dates of Service
            LocalDate fromDate = claim.getServiceDateFrom();
            LocalDate toDate = claim.getServiceDateTo();
            if (fromDate != null && toDate != null && !fromDate.equals(toDate)) {
                segments.add("DTP" + ELEM_SEP + "434" + ELEM_SEP + "RD8" + ELEM_SEP + fromDate.format(DATE_FMT_8) + "-" + toDate.format(DATE_FMT_8));
            } else if (fromDate != null) {
                segments.add("DTP" + ELEM_SEP + "472" + ELEM_SEP + "D8" + ELEM_SEP + fromDate.format(DATE_FMT_8));
            }

            // HI Diagnoses
            List<ClaimDiagnosis> diagnoses = claim.getDiagnoses();
            if (diagnoses != null && !diagnoses.isEmpty()) {
                StringBuilder hiBuilder = new StringBuilder("HI");
                for (int i = 0; i < diagnoses.size(); i++) {
                    ClaimDiagnosis cd = diagnoses.get(i);
                    String qualifier = (i == 0) ? "BK" : "BF";
                    String code = sanitizeCode(cd.getIcd10Code());
                    hiBuilder.append(ELEM_SEP).append(qualifier).append(COMP_SEP).append(code);
                }
                segments.add(hiBuilder.toString());
            }

            // Loop 2400: Service Lines
            List<ClaimLine> lines = claim.getLines();
            if (lines != null) {
                int lineSeq = 1;
                for (ClaimLine line : lines) {
                    segments.add("LX" + ELEM_SEP + lineSeq);

                    String cpt = sanitize(line.getCptCode());
                    String mods = line.getModifiers();
                    StringBuilder compCpt = new StringBuilder("HC").append(COMP_SEP).append(cpt);
                    if (mods != null && !mods.isBlank()) {
                        for (String m : mods.split(",")) {
                            if (!m.isBlank()) {
                                compCpt.append(COMP_SEP).append(sanitize(m.trim()));
                            }
                        }
                    }

                    BigDecimal lineCharge = line.getChargeAmount() != null ? line.getChargeAmount() : BigDecimal.ZERO;
                    int units = line.getUnits() != null ? line.getUnits() : 1;

                    // SV1 segment (SV101: procedure, SV102: line charge, SV103: UN, SV104: units, SV107: diagnosis pointer)
                    segments.add("SV1" + ELEM_SEP +
                            compCpt + ELEM_SEP +
                            lineCharge.toPlainString() + ELEM_SEP +
                            "UN" + ELEM_SEP +
                            units + ELEM_SEP +
                            ELEM_SEP + ELEM_SEP +
                            "1"); // Point to principal diagnosis

                    LocalDate lineDate = line.getServiceDate() != null ? line.getServiceDate() : fromDate;
                    if (lineDate != null) {
                        segments.add("DTP" + ELEM_SEP + "472" + ELEM_SEP + "D8" + ELEM_SEP + lineDate.format(DATE_FMT_8));
                    }

                    lineSeq++;
                }
            }
        }

        // 7. SE Trailer
        // Count from ST up to and including SE
        int transactionSegmentCount = (segments.size() - stStartIndex) + 1;
        String se = "SE" + ELEM_SEP + transactionSegmentCount + ELEM_SEP + tcn;
        segments.add(se);

        // 8. GE Trailer
        String ge = "GE" + ELEM_SEP + "1" + ELEM_SEP + gcn;
        segments.add(ge);

        // 9. IEA Trailer
        String iea = "IEA" + ELEM_SEP + "1" + ELEM_SEP + icn;
        segments.add(iea);

        // Assemble with segment terminators
        StringBuilder payloadBuilder = new StringBuilder();
        for (String seg : segments) {
            payloadBuilder.append(seg).append(SEG_TERM);
        }

        return new GeneratorResult(
                payloadBuilder.toString(),
                segments.size(),
                icn,
                gcn,
                tcn
        );
    }

    private static String padRight(String s, int n) {
        if (s == null) s = "";
        if (s.length() >= n) return s.substring(0, n);
        return String.format("%-" + n + "s", s);
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        // Strip X12 delimiters
        return s.replace("*", "").replace("~", "").replace(":", "").replace("^", "").trim();
    }

    private static String sanitizeCode(String s) {
        if (s == null) return "";
        // Strip decimals from ICD-10 codes for X12 compliance (e.g. E11.9 -> E119)
        return s.replace(".", "").replace("*", "").replace("~", "").replace(":", "").trim();
    }
}

