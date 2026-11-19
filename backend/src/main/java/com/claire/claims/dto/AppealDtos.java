package com.claire.claims.dto;

import com.claire.claims.domain.AppealOutcome;
import com.claire.claims.domain.ClaimStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public final class AppealDtos {

    private AppealDtos() { }

    // ----- write models ---------------------------------------------------

    public record FileAppealRequest(
            Long denialId,

            @NotBlank(message = "narrative is required to document medical/contractual basis of appeal")
            @Size(min = 5, max = 4000, message = "narrative must be between 5 and 4000 characters")
            String narrative,

            @Min(value = 1, message = "responseDeadlineDays must be at least 1 day")
            @Max(value = 365, message = "responseDeadlineDays cannot exceed 365 days")
            Integer responseDeadlineDays
    ) { }

    public record RecordOutcomeRequest(
            @NotNull(message = "outcome is required")
            AppealOutcome outcome,

            @NotNull(message = "decidedOn date is required")
            LocalDate decidedOn,

            @DecimalMin(value = "0.00", message = "recoveredAmount must be non-negative")
            @Digits(integer = 10, fraction = 2)
            BigDecimal recoveredAmount,

            @Size(max = 1000, message = "decisionNotes must not exceed 1000 characters")
            String decisionNotes
    ) { }

    // ----- read models ----------------------------------------------------

    public record AppealResponse(
            Long id,
            Long claimId,
            String claimNumber,
            Long denialId,
            String denialCode,
            Integer level,
            String levelDescription,
            LocalDate filedOn,
            LocalDate deadline,
            String narrative,
            AppealOutcome outcome,
            LocalDate decidedOn,
            BigDecimal recoveredAmount,
            String decisionNotes,
            ClaimStatus resultingClaimStatus,
            String filedBy,
            OffsetDateTime createdAt
    ) { }
}

