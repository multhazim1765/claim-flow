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
            String policyMemberId,
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
            String rawResponse
    ) { }

    public record EligibilityHistoryItem(
            Long id,
            Long patientId,
            String patientName,
            Long payerId,
            String payerName,
            LocalDate serviceDate,
            boolean active,
            OffsetDateTime checkedAt,
            BigDecimal copay,
            Integer coinsurancePercent,
            BigDecimal deductibleRemaining,
            boolean cached,
            String checkedBy
    ) { }
}

