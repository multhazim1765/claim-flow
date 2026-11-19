package com.claire.claims.service.remittance;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.domain.AdjustmentGroupCode;
import com.claire.claims.dto.RemittanceDtos.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic synthetic ANSI X12 835 / JSON parser for revenue cycle simulation.
 *
 * Notice: This parser is designed for revenue cycle simulation and testing.
 * It is not a certified WEDI/SNIP Level 1-7 EDI clearinghouse validator.
 */
@Component
public class SyntheticEra835Parser {

    private static final int MAX_PAYLOAD_BYTES = 5 * 1024 * 1024; // 5 MB
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Parses synthetic X12 835 text content into structured ImportRemittanceRequest.
     */
    public ImportRemittanceRequest parseX12(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessRuleException("835 content is empty or blank.");
        }
        if (content.length() > MAX_PAYLOAD_BYTES) {
            throw new BusinessRuleException("835 file exceeds maximum allowed size of 5 MB.");
        }

        // Basic sanity check against binary data
        for (int i = 0; i < Math.min(content.length(), 256); i++) {
            char ch = content.charAt(i);
            if (ch == 0 || (ch < 32 && ch != '\r' && ch != '\n' && ch != '\t')) {
                throw new BusinessRuleException("Malformed 835 file: binary or non-text control characters detected.");
            }
        }

        // Tokenize segments by '~' or newline
        String[] rawSegments = content.split("~|\\r?\\n");
        List<String> segments = new ArrayList<>();
        for (String s : rawSegments) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                segments.add(trimmed);
            }
        }

        if (segments.isEmpty()) {
            throw new BusinessRuleException("Malformed 835: no valid X12 segments found.");
        }

        String checkNumber = null;
        LocalDate checkDate = null;
        BigDecimal totalPaid = null;
        String paymentMethod = "ACH";
        String payerCode = null;

        List<RemittanceClaimInput> claims = new ArrayList<>();
        RemittanceClaimInput currentClaim = null;
        List<RemittanceAdjustmentInput> currentClaimAdjs = new ArrayList<>();
        List<RemittanceLineInput> currentLines = new ArrayList<>();
        RemittanceLineInput currentLine = null;
        List<RemittanceAdjustmentInput> currentLineAdjs = new ArrayList<>();

        for (String seg : segments) {
            String[] elements = seg.split("\\*");
            if (elements.length == 0) continue;
            String tag = elements[0].trim().toUpperCase();

            switch (tag) {
                case "BPR" -> {
                    // BPR*I*1240.00*C*ACH*...*20261120
                    if (elements.length > 2) {
                        totalPaid = parseMoney(elements[2], "BPR02 Total Paid");
                    }
                    if (elements.length > 4) {
                        paymentMethod = elements[4].trim().toUpperCase();
                        if (!paymentMethod.equals("ACH") && !paymentMethod.equals("CHK") && !paymentMethod.equals("NON")) {
                            paymentMethod = "ACH";
                        }
                    }
                    if (elements.length > 16) {
                        checkDate = parseDate(elements[16].trim(), "BPR16 Check Date");
                    }
                }
                case "TRN" -> {
                    // TRN*1*EFT-2026-99042*...
                    if (elements.length > 2) {
                        checkNumber = elements[2].trim();
                    }
                }
                case "N1" -> {
                    // N1*PR*Aetna*XX*ETIN123
                    if (elements.length > 1 && "PR".equalsIgnoreCase(elements[1].trim())) {
                        if (elements.length > 4) {
                            payerCode = elements[4].trim();
                        } else if (elements.length > 2) {
                            payerCode = elements[2].trim();
                        }
                    }
                }
                case "CLP" -> {
                    // Flush previous line & claim if present
                    if (currentLine != null) {
                        currentLines.add(finishLine(currentLine, currentLineAdjs));
                        currentLine = null;
                        currentLineAdjs = new ArrayList<>();
                    }
                    if (currentClaim != null) {
                        claims.add(finishClaim(currentClaim, currentClaimAdjs, currentLines));
                        currentClaim = null;
                        currentClaimAdjs = new ArrayList<>();
                        currentLines = new ArrayList<>();
                    }

                    // CLP*CLM-2026-000004*1*1450.00*1240.00*0.00*12*ICN-2026-9912
                    if (elements.length < 5) {
                        throw new BusinessRuleException("Malformed CLP segment: insufficient elements (" + seg + ")");
                    }
                    String claimNumber = elements[1].trim();
                    String statusCode = elements[2].trim();
                    BigDecimal billed = parseMoney(elements[3], "CLP03 Billed Amount");
                    BigDecimal paid = parseMoney(elements[4], "CLP04 Paid Amount");
                    BigDecimal patientResp = elements.length > 5 ? parseMoney(elements[5], "CLP05 Patient Responsibility") : BigDecimal.ZERO;
                    String icn = elements.length > 7 ? elements[7].trim() : null;

                    // Derive allowed amount: initially billed minus contractual if not explicitly in line
                    currentClaim = new RemittanceClaimInput(
                            claimNumber,
                            icn,
                            statusCode,
                            billed,
                            paid.add(patientResp), // temporary allowed, refined by adjustments
                            paid,
                            patientResp,
                            "22".equals(statusCode) ? "REVERSAL" : "ORIGINAL",
                            null,
                            null
                    );
                }
                case "SVC" -> {
                    // Flush previous line
                    if (currentLine != null) {
                        currentLines.add(finishLine(currentLine, currentLineAdjs));
                        currentLine = null;
                        currentLineAdjs = new ArrayList<>();
                    }

                    // SVC*HC:99214*1450.00*1240.00
                    if (elements.length < 4) {
                        throw new BusinessRuleException("Malformed SVC segment: insufficient elements (" + seg + ")");
                    }
                    String rawCpt = elements[1].trim();
                    String cptCode = rawCpt.contains(":") ? rawCpt.split(":")[1].trim() : rawCpt;
                    BigDecimal lineBilled = parseMoney(elements[2], "SVC02 Line Billed");
                    BigDecimal linePaid = parseMoney(elements[3], "SVC03 Line Paid");

                    currentLine = new RemittanceLineInput(
                            cptCode,
                            currentLines.size() + 1,
                            lineBilled,
                            linePaid,
                            linePaid,
                            null
                    );
                }
                case "CAS" -> {
                    // CAS*CO*45*210.00 or CAS*PR*1*100.00*2*50.00
                    List<RemittanceAdjustmentInput> adjs = parseCasSegment(elements);
                    if (currentLine != null) {
                        currentLineAdjs.addAll(adjs);
                    } else if (currentClaim != null) {
                        currentClaimAdjs.addAll(adjs);
                    }
                }
                default -> {
                    // Other segments (ISA, GS, ST, DTM, SE, GE, IEA) are acknowledged and skipped
                }
            }
        }

        // Flush last line and claim
        if (currentLine != null) {
            currentLines.add(finishLine(currentLine, currentLineAdjs));
        }
        if (currentClaim != null) {
            claims.add(finishClaim(currentClaim, currentClaimAdjs, currentLines));
        }

        if (checkNumber == null || checkNumber.isBlank()) {
            throw new BusinessRuleException("Malformed 835: missing TRN check/EFT trace number.");
        }
        if (checkDate == null) {
            checkDate = LocalDate.now();
        }
        if (totalPaid == null) {
            totalPaid = claims.stream().map(RemittanceClaimInput::paidAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        return new ImportRemittanceRequest(
                null,
                payerCode,
                checkNumber,
                checkDate,
                paymentMethod,
                totalPaid,
                claims
        );
    }

    private RemittanceLineInput finishLine(RemittanceLineInput line, List<RemittanceAdjustmentInput> adjs) {
        BigDecimal allowed = line.paidAmount();
        for (RemittanceAdjustmentInput adj : adjs) {
            if (adj.groupCode() == AdjustmentGroupCode.PR) {
                allowed = allowed.add(adj.amount());
            }
        }
        return new RemittanceLineInput(
                line.cptCode(),
                line.lineNumber(),
                line.billedAmount(),
                allowed,
                line.paidAmount(),
                adjs
        );
    }

    private RemittanceClaimInput finishClaim(RemittanceClaimInput claim, List<RemittanceAdjustmentInput> claimAdjs, List<RemittanceLineInput> lines) {
        // Compute allowed amount: Paid + PR adjustments
        BigDecimal totalPr = claim.patientResponsibility() != null ? claim.patientResponsibility() : BigDecimal.ZERO;
        BigDecimal allowed = claim.paidAmount().add(totalPr);

        return new RemittanceClaimInput(
                claim.claimNumber(),
                claim.payerIcn(),
                claim.clpStatusCode(),
                claim.billedAmount(),
                allowed,
                claim.paidAmount(),
                totalPr,
                claim.entryType(),
                lines,
                claimAdjs
        );
    }

    private List<RemittanceAdjustmentInput> parseCasSegment(String[] elements) {
        List<RemittanceAdjustmentInput> result = new ArrayList<>();
        if (elements.length < 4) return result;

        String rawGroup = elements[1].trim().toUpperCase();
        AdjustmentGroupCode group;
        try {
            group = AdjustmentGroupCode.valueOf(rawGroup);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Unknown CAS adjustment group code: " + rawGroup + ". Allowed: CO, PR, OA, PI.");
        }

        // CAS can contain repeated pairs (reason, amount) or triplets (reason, amount, quantity)
        int idx = 2;
        while (idx + 1 < elements.length) {
            String reason = elements[idx].trim();
            if (reason.isEmpty()) break;
            BigDecimal amt = parseMoney(elements[idx + 1], "CAS Amount for " + reason);
            result.add(new RemittanceAdjustmentInput(group, reason, null, amt));

            if (idx + 3 < elements.length && elements[idx + 3].contains(".")) {
                idx += 2; // Next is a pair: elements[idx+2] is reason, elements[idx+3] is amount
            } else if (idx + 4 < elements.length && elements[idx + 4].contains(".")) {
                idx += 3; // Next is a triplet: elements[idx+2] is quantity, elements[idx+3] is reason
            } else {
                idx += 2;
            }
        }
        return result;
    }

    private BigDecimal parseMoney(String value, String fieldName) {
        try {
            return new BigDecimal(value.trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            throw new BusinessRuleException("Invalid numeric amount for " + fieldName + ": '" + value + "'");
        }
    }

    private LocalDate parseDate(String value, String fieldName) {
        try {
            if (value.length() == 8) {
                return LocalDate.parse(value, YYYYMMDD);
            }
            return LocalDate.parse(value, ISO_DATE);
        } catch (DateTimeParseException e) {
            throw new BusinessRuleException("Invalid date format for " + fieldName + ": '" + value + "'. Expected YYYYMMDD or YYYY-MM-DD.");
        }
    }
}

