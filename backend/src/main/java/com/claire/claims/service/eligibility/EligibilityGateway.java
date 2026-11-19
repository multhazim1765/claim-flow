package com.claire.claims.service.eligibility;

import com.claire.claims.domain.InsurancePolicy;
import com.claire.claims.domain.Patient;
import com.claire.claims.domain.Payer;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payer gateway contract for ANSI X12 270/271 Real-Time Eligibility checks.
 * Separates core billing logic from external clearinghouse/payer protocols.
 */
public interface EligibilityGateway {

    GatewayResponse verify(Patient patient, Payer payer, InsurancePolicy policy,
                           LocalDate serviceDate, String serviceTypeCode);

    record GatewayResponse(
            boolean active,
            LocalDate planBegin,
            LocalDate planEnd,
            BigDecimal copay,
            Integer coinsurancePercent,
            BigDecimal deductibleTotal,
            BigDecimal deductibleRemaining,
            BigDecimal outOfPocketRemaining,
            String rawResponse
    ) {}
}

