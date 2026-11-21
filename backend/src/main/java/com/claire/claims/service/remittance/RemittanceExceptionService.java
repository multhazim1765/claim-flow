package com.claire.claims.service.remittance;

import com.claire.claims.common.ApiExceptions.ConflictException;
import com.claire.claims.common.ApiExceptions.NotFoundException;
import com.claire.claims.domain.Claim;
import com.claire.claims.domain.RemittanceBatch;
import com.claire.claims.domain.RemittanceClaimException;
import com.claire.claims.dto.RemittanceExceptionDtos.ExceptionResolutionRequest;
import com.claire.claims.dto.RemittanceExceptionDtos.RemittanceExceptionResponse;
import com.claire.claims.repository.ClaimRepository;
import com.claire.claims.repository.RemittanceBatchRepository;
import com.claire.claims.repository.RemittanceClaimExceptionRepository;
import com.claire.claims.security.CurrentUserProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class RemittanceExceptionService {

    private final RemittanceClaimExceptionRepository exceptionRepository;
    private final RemittanceBatchRepository batchRepository;
    private final ClaimRepository claimRepository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    @Autowired
    public RemittanceExceptionService(RemittanceClaimExceptionRepository exceptionRepository,
                                      RemittanceBatchRepository batchRepository,
                                      ClaimRepository claimRepository,
                                      CurrentUserProvider currentUserProvider) {
        this(exceptionRepository, batchRepository, claimRepository, currentUserProvider, Clock.systemDefaultZone());
    }

    public RemittanceExceptionService(RemittanceClaimExceptionRepository exceptionRepository,
                                      RemittanceBatchRepository batchRepository,
                                      ClaimRepository claimRepository,
                                      CurrentUserProvider currentUserProvider,
                                      Clock clock) {
        this.exceptionRepository = exceptionRepository;
        this.batchRepository = batchRepository;
        this.claimRepository = claimRepository;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RemittanceExceptionResponse> getExceptions(String status) {
        List<RemittanceClaimException> list;
        if (status != null && !status.isBlank()) {
            list = exceptionRepository.findByStatusOrderByIdAsc(status.trim().toUpperCase());
        } else {
            list = exceptionRepository.findAllByOrderByIdAsc();
        }
        return list.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RemittanceExceptionResponse getException(Long id) {
        RemittanceClaimException exc = exceptionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("RemittanceClaimException", id));
        return toResponse(exc);
    }

    @Transactional
    public RemittanceExceptionResponse resolveException(Long id, ExceptionResolutionRequest request) {
        RemittanceClaimException exc = exceptionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("RemittanceClaimException", id));

        if (!"UNRESOLVED".equalsIgnoreCase(exc.getStatus())) {
            throw new ConflictException("Remittance exception " + id + " is already " + exc.getStatus());
        }

        if (request.matchedClaimId() != null) {
            Claim matched = claimRepository.findById(request.matchedClaimId())
                    .orElseThrow(() -> new NotFoundException("Claim", request.matchedClaimId()));
            exc.setMatchedClaim(matched);
            if (exc.getVoucher() != null) {
                exc.getVoucher().setClaim(matched);
            }
        }

        String actor = currentUserProvider.username();
        exc.setStatus("RESOLVED");
        exc.setResolutionAction(request.action().trim());
        exc.setResolutionNotes(request.notes().trim());
        exc.setResolvedAt(OffsetDateTime.now(clock));
        exc.setResolvedBy(actor);
        exc = exceptionRepository.save(exc);

        // Update batch exception count
        RemittanceBatch batch = exc.getRemittance();
        if (batch != null && batch.getExceptionCount() > 0) {
            batch.setExceptionCount(batch.getExceptionCount() - 1);
            batchRepository.save(batch);
        }

        return toResponse(exc);
    }

    private RemittanceExceptionResponse toResponse(RemittanceClaimException exc) {
        String batchControl = exc.getRemittance() != null ? exc.getRemittance().getBatchControlNumber() : null;
        Long voucherId = exc.getVoucher() != null ? exc.getVoucher().getId() : null;
        Long matchedClaimId = exc.getMatchedClaim() != null ? exc.getMatchedClaim().getId() : null;
        String matchedClaimNumber = exc.getMatchedClaim() != null ? exc.getMatchedClaim().getClaimNumber() : null;

        return new RemittanceExceptionResponse(
                exc.getId(),
                exc.getRemittance() != null ? exc.getRemittance().getId() : null,
                batchControl,
                voucherId,
                exc.getClaimNumber(),
                exc.getPatientName(),
                exc.getServiceDate(),
                exc.getBilledAmount(),
                exc.getPaidAmount(),
                exc.getExceptionType(),
                exc.getErrorMessage(),
                exc.getStatus(),
                exc.getResolutionAction(),
                exc.getResolutionNotes(),
                matchedClaimId,
                matchedClaimNumber,
                exc.getResolvedAt(),
                exc.getResolvedBy()
        );
    }
}

