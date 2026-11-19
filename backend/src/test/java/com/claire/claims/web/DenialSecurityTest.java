package com.claire.claims.web;

import com.claire.claims.domain.DenialCategory;
import com.claire.claims.domain.DenialStatus;
import com.claire.claims.dto.ClaimDtos.PageResponse;
import com.claire.claims.dto.DenialDtos.*;
import com.claire.claims.security.CurrentUserProvider;
import com.claire.claims.service.DenialService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DenialSecurityTest {

    @Mock
    private DenialService denialService;

    @InjectMocks
    private DenialController denialController;

    private CurrentUserProvider currentUserProvider;

    @BeforeEach
    void setUp() {
        currentUserProvider = new CurrentUserProvider();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // =========================================================================
    // 1. Role Annotation Security Audits (@PreAuthorize)
    // =========================================================================

    @Test
    @DisplayName("Audit: GET /api/denials has @PreAuthorize('hasAnyRole(\\'ADMIN\\',\\'BILLER\\',\\'VIEWER\\')')")
    void testList_PreAuthorizeAnnotation() throws Exception {
        Method method = DenialController.class.getMethod("list", Long.class, DenialCategory.class, DenialStatus.class, String.class, Pageable.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN','BILLER','VIEWER')");
    }

    @Test
    @DisplayName("Audit: GET /api/denials/summary has @PreAuthorize('hasAnyRole(\\'ADMIN\\',\\'BILLER\\',\\'VIEWER\\')')")
    void testSummary_PreAuthorizeAnnotation() throws Exception {
        Method method = DenialController.class.getMethod("summary");
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN','BILLER','VIEWER')");
    }

    @Test
    @DisplayName("Audit: GET /api/denials/{id} has @PreAuthorize('hasAnyRole(\\'ADMIN\\',\\'BILLER\\',\\'VIEWER\\')')")
    void testGetById_PreAuthorizeAnnotation() throws Exception {
        Method method = DenialController.class.getMethod("getById", Long.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN','BILLER','VIEWER')");
    }

    @Test
    @DisplayName("Audit: POST /api/denials is restricted to ADMIN and BILLER (VIEWER excluded)")
    void testCreate_PreAuthorizeAnnotation() throws Exception {
        Method method = DenialController.class.getMethod("create", CreateDenialRequest.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN','BILLER')");
        assertThat(annotation.value()).doesNotContain("VIEWER");
    }

    // =========================================================================
    // 2. Controller Dispatch & Response Contracts
    // =========================================================================

    @Test
    @DisplayName("DenialController.list dispatches to DenialService.search and returns PageResponse")
    void testControllerList() {
        PageResponse<DenialWorklistItem> mockPage = new PageResponse<>(Collections.emptyList(), 0, 20, 0, 0, true);
        when(denialService.search(any(), any(), any(), any(), any(Pageable.class))).thenReturn(mockPage);

        PageResponse<DenialWorklistItem> response = denialController.list(2L, DenialCategory.AUTHORIZATION, DenialStatus.OPEN, "CRITICAL", PageRequest.of(0, 20));

        assertThat(response).isNotNull();
        assertThat(response.totalElements()).isEqualTo(0);
        verify(denialService).search(eq(2L), eq(DenialCategory.AUTHORIZATION), eq(DenialStatus.OPEN), eq("CRITICAL"), any(Pageable.class));
    }

    @Test
    @DisplayName("DenialController.summary dispatches to DenialService.getSummary")
    void testControllerSummary() {
        DenialSummaryResponse mockSummary = new DenialSummaryResponse(5, new BigDecimal("4500.00"), 2, 1, 1, 1, Map.of());
        when(denialService.getSummary()).thenReturn(mockSummary);

        DenialSummaryResponse response = denialController.summary();

        assertThat(response).isNotNull();
        assertThat(response.totalOpenDenials()).isEqualTo(5);
        assertThat(response.totalOpenDeniedAmount()).isEqualByComparingTo("4500.00");
        verify(denialService).getSummary();
    }

    @Test
    @DisplayName("DenialController.create dispatches to DenialService.createDenial and returns 201 Created")
    void testControllerCreate() {
        CreateDenialRequest request = new CreateDenialRequest(
                4L, 2L, "CO-197", "N24", DenialCategory.AUTHORIZATION,
                "Precertification absent", LocalDate.now().minusDays(5), LocalDate.now().plusDays(30), new BigDecimal("1450.00")
        );
        DenialResponse mockResponse = new DenialResponse(
                10L, 4L, "CLM-2026-000004", 2L, "Aetna Health", "CO-197", "N24",
                DenialCategory.AUTHORIZATION, "Precertification absent",
                LocalDate.now().minusDays(5), LocalDate.now().plusDays(30), 30, "WARNING",
                new BigDecimal("1450.00"), DenialStatus.OPEN, "biller", OffsetDateTime.now()
        );

        when(denialService.createDenial(request)).thenReturn(mockResponse);

        ResponseEntity<DenialResponse> entity = denialController.create(request);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(entity.getBody()).isNotNull();
        assertThat(entity.getBody().id()).isEqualTo(10L);
        assertThat(entity.getBody().denialCode()).isEqualTo("CO-197");
        verify(denialService).createDenial(request);
    }

    // =========================================================================
    // 3. Security Context & Role Resolution
    // =========================================================================

    @Test
    @DisplayName("CurrentUserProvider correctly identifies ADMIN, BILLER, and VIEWER roles")
    void testRoleResolution() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("dana", "n/a", "ROLE_VIEWER")
        );
        assertThat(currentUserProvider.username()).isEqualTo("dana");
        assertThat(currentUserProvider.hasRole("VIEWER")).isTrue();
        assertThat(currentUserProvider.hasRole("BILLER")).isFalse();
        assertThat(currentUserProvider.hasRole("ADMIN")).isFalse();

        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("biller", "n/a", "ROLE_BILLER")
        );
        assertThat(currentUserProvider.username()).isEqualTo("biller");
        assertThat(currentUserProvider.hasRole("BILLER")).isTrue();
        assertThat(currentUserProvider.hasRole("VIEWER")).isFalse();

        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("admin", "n/a", "ROLE_ADMIN")
        );
        assertThat(currentUserProvider.username()).isEqualTo("admin");
        assertThat(currentUserProvider.hasRole("ADMIN")).isTrue();
    }

    @Test
    @DisplayName("CurrentUserProvider handles anonymous/null security context safely")
    void testAnonymousResolution() {
        SecurityContextHolder.clearContext();
        assertThat(currentUserProvider.username()).isEqualTo("system");
        assertThat(currentUserProvider.hasRole("ADMIN")).isFalse();
        assertThat(currentUserProvider.hasRole("BILLER")).isFalse();
        assertThat(currentUserProvider.hasRole("VIEWER")).isFalse();
    }
}
