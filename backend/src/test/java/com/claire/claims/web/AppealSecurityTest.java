package com.claire.claims.web;

import com.claire.claims.domain.AppealOutcome;
import com.claire.claims.domain.ClaimStatus;
import com.claire.claims.dto.AppealDtos.*;
import com.claire.claims.service.AppealService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppealSecurityTest {

    @Mock
    private AppealService appealService;

    @InjectMocks
    private AppealController appealController;

    // =========================================================================
    // 1. Role Annotation Security Audits (@PreAuthorize)
    // =========================================================================

    @Test
    @DisplayName("Audit: POST /api/claims/{id}/appeal is restricted to ADMIN and BILLER (VIEWER excluded)")
    void testFileAppeal_PreAuthorize() throws Exception {
        Method method = AppealController.class.getMethod("fileAppeal", Long.class, FileAppealRequest.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN','BILLER')");
        assertThat(annotation.value()).doesNotContain("VIEWER");
    }

    @Test
    @DisplayName("Audit: POST /api/appeals/{id}/outcome is restricted to ADMIN and BILLER (VIEWER excluded)")
    void testRecordOutcome_PreAuthorize() throws Exception {
        Method method = AppealController.class.getMethod("recordOutcome", Long.class, RecordOutcomeRequest.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN','BILLER')");
        assertThat(annotation.value()).doesNotContain("VIEWER");
    }

    @Test
    @DisplayName("Audit: GET /api/claims/{id}/appeals allows ADMIN, BILLER, and VIEWER")
    void testListAppeals_PreAuthorize() throws Exception {
        Method method = AppealController.class.getMethod("listAppeals", Long.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN','BILLER','VIEWER')");
    }

    @Test
    @DisplayName("Audit: GET /api/appeals/{id} allows ADMIN, BILLER, and VIEWER")
    void testGetById_PreAuthorize() throws Exception {
        Method method = AppealController.class.getMethod("getById", Long.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN','BILLER','VIEWER')");
    }

    // =========================================================================
    // 2. Controller Routing & Status Codes
    // =========================================================================

    @Test
    @DisplayName("AppealController.fileAppeal dispatches to service and returns 201 Created")
    void testControllerFileAppeal() {
        FileAppealRequest req = new FileAppealRequest(1L, "Formal appeal narrative", 60);
        AppealResponse res = new AppealResponse(
                10L, 4L, "CLM-2026-000004", 1L, "CO-197", 1, "Level 1: Reconsideration",
                LocalDate.now(), LocalDate.now().plusDays(60), "Narrative", null, null,
                BigDecimal.ZERO, null, ClaimStatus.APPEALED, "biller", OffsetDateTime.now()
        );

        when(appealService.fileAppeal(eq(4L), any(FileAppealRequest.class))).thenReturn(res);

        ResponseEntity<AppealResponse> entity = appealController.fileAppeal(4L, req);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(entity.getBody()).isNotNull();
        assertThat(entity.getBody().id()).isEqualTo(10L);
        assertThat(entity.getBody().level()).isEqualTo(1);
        verify(appealService).fileAppeal(eq(4L), any(FileAppealRequest.class));
    }

    @Test
    @DisplayName("AppealController.recordOutcome dispatches to service and returns 200 OK")
    void testControllerRecordOutcome() {
        RecordOutcomeRequest req = new RecordOutcomeRequest(AppealOutcome.OVERTURNED, LocalDate.now(), new BigDecimal("1450.00"), "Notes");
        AppealResponse res = new AppealResponse(
                10L, 4L, "CLM-2026-000004", 1L, "CO-197", 1, "Level 1: Reconsideration",
                LocalDate.now().minusDays(10), LocalDate.now().plusDays(50), "Narrative",
                AppealOutcome.OVERTURNED, LocalDate.now(), new BigDecimal("1450.00"),
                "Notes", ClaimStatus.ACCEPTED, "biller", OffsetDateTime.now()
        );

        when(appealService.recordOutcome(eq(10L), any(RecordOutcomeRequest.class))).thenReturn(res);

        ResponseEntity<AppealResponse> entity = appealController.recordOutcome(10L, req);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entity.getBody()).isNotNull();
        assertThat(entity.getBody().outcome()).isEqualTo(AppealOutcome.OVERTURNED);
        verify(appealService).recordOutcome(eq(10L), any(RecordOutcomeRequest.class));
    }

    @Test
    @DisplayName("AppealController.listAppeals dispatches to service and returns 200 OK list")
    void testControllerListAppeals() {
        when(appealService.listAppeals(4L)).thenReturn(Collections.emptyList());

        List<AppealResponse> result = appealController.listAppeals(4L);

        assertThat(result).isNotNull();
        verify(appealService).listAppeals(4L);
    }
}

