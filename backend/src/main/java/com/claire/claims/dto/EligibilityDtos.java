package com.claire.claims.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public final class EligibilityDtos {

    private EligibilityDtos() { }

    public record EligibilityCheckRequest(
            @NotNull(message = "patientId is required")
            Long patientId,

            @NotNull(message = "payerId is required")
            Long payerId,

            Long policyId,

            @NotNull(message = "serviceDate is required")
            LocalDate serviceDate,

            String serviceTypeCode,

            Boolean forceRefresh
    ) { }

    public record EligibilityCheckResponse(
            Long id,
            Long patientId,
            String patientName,
            String patientMrn,
            Long payerId,
            String payerName,
            Long policyId,
            String policyMemberId,
            String serviceTypeCode,
            boolean active,
            OffsetDateTime checkedAt,
            LocalDate planBegin,
            LocalDate planEnd,
            BigDecimal copay,
            Integer coinsurancePercent,
            BigDecimal deductibleTotal,
            BigDecimal deductibleRemaining,
            BigDecimal outOfPocketRemaining,
            boolean cached,
            String benefitSource,
            String sourceLabel,
            Long cacheAgeSeconds,
            String rawResponse
    ) { }

    public record EligibilityHistoryItem(
            Long id,
            Long patientId,
            String patientName,
            Long payerId,
            String payerName,
            Long policyId,
            String policyMemberId,
            String serviceTypeCode,
            LocalDate serviceDate,
            boolean active,
            OffsetDateTime checkedAt,
            BigDecimal copay,
            Integer coinsurancePercent,
            BigDecimal deductibleRemaining,
            boolean cached,
            String benefitSource,
            String checkedBy
    ) { }
}

