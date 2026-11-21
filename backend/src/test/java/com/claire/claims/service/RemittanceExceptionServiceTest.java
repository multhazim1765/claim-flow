package com.claire.claims.service;

import com.claire.claims.common.ApiExceptions.ConflictException;
import com.claire.claims.common.ApiExceptions.NotFoundException;
import com.claire.claims.domain.Claim;
import com.claire.claims.domain.RemittanceBatch;
import com.claire.claims.domain.RemittanceClaimException;
import com.claire.claims.domain.RemittanceClaimVoucher;
import com.claire.claims.dto.RemittanceExceptionDtos.ExceptionResolutionRequest;
import com.claire.claims.dto.RemittanceExceptionDtos.RemittanceExceptionResponse;
import com.claire.claims.repository.ClaimRepository;
import com.claire.claims.repository.RemittanceBatchRepository;
import com.claire.claims.repository.RemittanceClaimExceptionRepository;
import com.claire.claims.security.CurrentUserProvider;
import com.claire.claims.service.remittance.RemittanceExceptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemittanceExceptionServiceTest {

    @Mock
    private RemittanceClaimExceptionRepository exceptionRepository;

    @Mock
    private RemittanceBatchRepository batchRepository;

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private RemittanceExceptionService service;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneId.of("UTC"));
        service = new RemittanceExceptionService(
                exceptionRepository,
                batchRepository,
                claimRepository,
                currentUserProvider,
                fixedClock
        );
        lenient().when(currentUserProvider.username()).thenReturn("biller_sam");
    }

    @Test
    @DisplayName("Lists all exceptions or filters by status")
    void testListExceptions() {
        RemittanceClaimException exc1 = new RemittanceClaimException();
        exc1.setId(1L);
        exc1.setClaimNumber("CLM-UNK-001");
        exc1.setStatus("UNRESOLVED");

        RemittanceClaimException exc2 = new RemittanceClaimException();
        exc2.setId(2L);
        exc2.setClaimNumber("CLM-UNK-002");
        exc2.setStatus("RESOLVED");

        when(exceptionRepository.findAllByOrderByIdAsc()).thenReturn(List.of(exc1, exc2));
        when(exceptionRepository.findByStatusOrderByIdAsc("UNRESOLVED")).thenReturn(List.of(exc1));

        List<RemittanceExceptionResponse> all = service.getExceptions(null);
        assertThat(all).hasSize(2);

        List<RemittanceExceptionResponse> filtered = service.getExceptions("UNRESOLVED");
        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).claimNumber()).isEqualTo("CLM-UNK-001");
    }

    @Test
    @DisplayName("Successfully resolves an open remittance exception with matched claim")
    void testResolveExceptionSuccessfully() {
        RemittanceBatch batch = new RemittanceBatch();
        batch.setId(5L);
        batch.setBatchControlNumber("BATCH-2026-005");
        batch.setExceptionCount(2);

        RemittanceClaimVoucher voucher = new RemittanceClaimVoucher();
        voucher.setId(10L);

        RemittanceClaimException exc = new RemittanceClaimException();
        exc.setId(1L);
        exc.setRemittance(batch);
        exc.setVoucher(voucher);
        exc.setClaimNumber("CLM-2026-000008-WRONG");
        exc.setBilledAmount(new BigDecimal("675.00"));
        exc.setPaidAmount(new BigDecimal("540.00"));
        exc.setExceptionType("UNMATCHED_CLAIM");
        exc.setStatus("UNRESOLVED");

        Claim matchedClaim = new Claim();
        matchedClaim.setId(8L);
        matchedClaim.setClaimNumber("CLM-2026-000008");

        when(exceptionRepository.findById(1L)).thenReturn(Optional.of(exc));
        when(claimRepository.findById(8L)).thenReturn(Optional.of(matchedClaim));
        when(exceptionRepository.save(any(RemittanceClaimException.class))).thenAnswer(i -> i.getArgument(0));

        ExceptionResolutionRequest request = new ExceptionResolutionRequest(
                "MANUAL_MATCH",
                8L,
                "Matched to Claim 8 following provider typo correction"
        );

        RemittanceExceptionResponse response = service.resolveException(1L, request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo("RESOLVED");
        assertThat(response.resolutionAction()).isEqualTo("MANUAL_MATCH");
        assertThat(response.resolutionNotes()).contains("Matched to Claim 8");
        assertThat(response.matchedClaimId()).isEqualTo(8L);
        assertThat(response.matchedClaimNumber()).isEqualTo("CLM-2026-000008");
        assertThat(response.resolvedBy()).isEqualTo("biller_sam");

        // Verify voucher linked to matched claim
        assertThat(voucher.getClaim()).isEqualTo(matchedClaim);
        // Verify batch exception count decremented
        assertThat(batch.getExceptionCount()).isEqualTo(1);
        verify(batchRepository).save(batch);
    }

    @Test
    @DisplayName("Resolving an already resolved exception throws ConflictException")
    void testResolveAlreadyResolvedExceptionThrowsConflict() {
        RemittanceClaimException exc = new RemittanceClaimException();
        exc.setId(1L);
        exc.setStatus("RESOLVED");

        when(exceptionRepository.findById(1L)).thenReturn(Optional.of(exc));

        ExceptionResolutionRequest request = new ExceptionResolutionRequest("DISMISS", null, "Already done");

        assertThatThrownBy(() -> service.resolveException(1L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("is already RESOLVED");
    }
}
