package com.claire.claims.service.eligibility;

import com.claire.claims.domain.InsurancePolicy;
import com.claire.claims.domain.Patient;
import com.claire.claims.domain.Payer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Educational simulated implementation of ANSI X12 270/271 real-time eligibility inquiry.
 * Generates standards-compliant synthetic educational benefit structures without connecting to any real payer.
 * All default benefit figures are clearly fictional demonstration archetypes and do not represent statutory determinations.
 */
@Component
public class SimulatedEligibilityGateway implements EligibilityGateway {

    private static final DateTimeFormatter D8 = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static final String SOURCE_SIMULATED_DEFAULT = "SIMULATED_DEFAULT";
    public static final String SOURCE_CUSTOM_DEMO = "SIMULATED_CUSTOM_DEMO_PROFILE";

    @Override
    public GatewayResponse verify(Patient patient, Payer payer, InsurancePolicy policy,
                                  LocalDate serviceDate, String serviceTypeCode) {
        LocalDate effDate = policy != null ? policy.getEffectiveDate() : serviceDate.withDayOfYear(1);
        LocalDate termDate = policy != null ? policy.getTerminationDate() : null;
        String memberId = policy != null ? policy.getMemberId() : "MB-" + patient.getMrn();

        // Check active coverage
        boolean active = policy != null
                ? policy.isActiveOn(serviceDate)
                : (serviceDate != null && !serviceDate.isBefore(effDate) && (termDate == null || !serviceDate.isAfter(termDate)));

        String benefitSource;
        BigDecimal copay;
        Integer coinsurance;
        BigDecimal dedTotal;
        BigDecimal dedRemaining;
        BigDecimal oopRemaining;

        // Custom demonstration profile has highest priority when complete
        if (policy != null && policy.hasCompleteDemoBenefitProfile()) {
            copay = policy.getDemoCopay();
            coinsurance = policy.getDemoCoinsurancePercent();
            dedTotal = policy.getDemoDeductibleTotal();
            dedRemaining = policy.getDemoDeductibleRemaining();
            oopRemaining = policy.getDemoOutOfPocketRemaining();
            benefitSource = SOURCE_CUSTOM_DEMO;
        } else {
            benefitSource = SOURCE_SIMULATED_DEFAULT;
            // Clearly fictional educational demonstration benefit defaults (not statutory CMS figures)
            String planType = payer != null && payer.getPlanType() != null ? payer.getPlanType().name() : "COMMERCIAL";
            switch (planType) {
                case "MEDICARE" -> {
                    // Fictional demonstration tier (illustrative only - not statutory CMS figures or universal copay)
                    copay = new BigDecimal("30.00");
                    coinsurance = 20;
                    dedTotal = new BigDecimal("300.00");
                    dedRemaining = new BigDecimal("100.00");
                    oopRemaining = new BigDecimal("3000.00");
                }
                case "MEDICAID" -> {
                    copay = BigDecimal.ZERO;
                    coinsurance = 0;
                    dedTotal = BigDecimal.ZERO;
                    dedRemaining = BigDecimal.ZERO;
                    oopRemaining = BigDecimal.ZERO;
                }
                case "TRICARE" -> {
                    copay = new BigDecimal("25.00");
                    coinsurance = 15;
                    dedTotal = new BigDecimal("200.00");
                    dedRemaining = new BigDecimal("50.00");
                    oopRemaining = new BigDecimal("2000.00");
                }
                default -> { // COMMERCIAL / SELF_PAY / etc.
                    copay = new BigDecimal("40.00");
                    coinsurance = 20;
                    dedTotal = new BigDecimal("1500.00");
                    dedRemaining = new BigDecimal("500.00");
                    oopRemaining = new BigDecimal("4000.00");
                }
            }
        }

        if (!active) {
            copay = BigDecimal.ZERO;
            coinsurance = 0;
            dedTotal = BigDecimal.ZERO;
            dedRemaining = BigDecimal.ZERO;
            oopRemaining = BigDecimal.ZERO;
        }

        String code = (serviceTypeCode != null && !serviceTypeCode.isBlank()) ? serviceTypeCode : "30";

        String rawX12 = generateSynthetic271(patient, payer, memberId, active, serviceDate,
                effDate, termDate, copay, coinsurance, dedTotal, dedRemaining, code);

        return new GatewayResponse(
                active,
                effDate,
                termDate,
                copay,
                coinsurance,
                dedTotal,
                dedRemaining,
                oopRemaining,
                rawX12,
                benefitSource
        );
    }

    private String generateSynthetic271(Patient patient, Payer payer, String memberId, boolean active,
                                        LocalDate serviceDate, LocalDate effDate, LocalDate termDate,
                                        BigDecimal copay, Integer coinsurance, BigDecimal dedTotal,
                                        BigDecimal dedRemaining, String serviceTypeCode) {
        String today = LocalDate.now().format(D8);
        String sDate = serviceDate != null ? serviceDate.format(D8) : today;
        String eff = effDate != null ? effDate.format(D8) : today;
        String payerCode = payer != null ? payer.getPayerCode() : "PAYER";
        String eligibilityCode = active ? "1" : "6"; // 1 = Active, 6 = Inactive
        String code = (serviceTypeCode != null && !serviceTypeCode.isBlank()) ? serviceTypeCode : "30";

        return "ISA*00*          *00*          *ZZ*CLAIMFLOW      *ZZ*" + payerCode + "        *"
                + today.substring(2) + "*1200*U*00501*000000001*0*P*:~"
                + "GS*HB*CLAIMFLOW*" + payerCode + "*" + today + "*1200*1*X*005010X279A1~"
                + "ST*271*0001*005010X279A1~"
                + "BHT*0022*11*REQ001*" + today + "*1200~"
                + "HL*1**20*1~"
                + "NM1*PR*2*" + (payer != null ? payer.getName().toUpperCase() : "PAYER") + "*****PI*" + payerCode + "~"
                + "HL*2*1*21*1~"
                + "NM1*1P*1*PROVIDER*CLINIC****XX*1999999999~"
                + "HL*3*2*22*0~"
                + "NM1*IL*1*" + patient.getLastName().toUpperCase() + "*" + patient.getFirstName().toUpperCase() + "****MI*" + memberId + "~"
                + "DMG*D8*" + patient.getDateOfBirth().format(D8) + "~"
                + "DTP*291*D8*" + eff + "~"
                + "EB*" + eligibilityCode + "**" + code + "*" + (payer != null ? payer.getName() : "") + "**" + dedTotal + "*" + dedRemaining + "~"
                + (active ? "EB*B**" + code + "*" + (payer != null ? payer.getName() : "") + "**" + copay + "~" : "")
                + (active ? "EB*C**" + code + "*" + (payer != null ? payer.getName() : "") + "***." + coinsurance + "~" : "")
                + "SE*13*0001~"
                + "GE*1*1~"
                + "IEA*1*000000001~";
    }
}

