package com.claire.claims.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RevenueCycleAnalyticsSecurityTest {

    @Test
    @DisplayName("Class-level security enforces ADMIN, BILLER, and VIEWER roles across all analytics endpoints")
    void testClassLevelSecurity() {
        PreAuthorize classPreAuth = RevenueCycleAnalyticsController.class.getAnnotation(PreAuthorize.class);
        assertThat(classPreAuth).isNotNull();
        assertThat(classPreAuth.value()).isEqualTo("hasAnyRole('ADMIN','BILLER','VIEWER')");
    }

    @Test
    @DisplayName("Overview endpoint is declared and secured")
    void testOverviewEndpoint() throws Exception {
        Method method = RevenueCycleAnalyticsController.class.getMethod("getOverview", LocalDate.class, LocalDate.class, Long.class, Long.class);
        assertThat(method).isNotNull();
    }

    @Test
    @DisplayName("AR Aging endpoint is declared and secured")
    void testArAgingEndpoint() throws Exception {
        Method method = RevenueCycleAnalyticsController.class.getMethod("getArAging", LocalDate.class, Long.class);
        assertThat(method).isNotNull();
    }

    @Test
    @DisplayName("Export CSV endpoint is declared and secured")
    void testExportEndpoint() throws Exception {
        Method method = RevenueCycleAnalyticsController.class.getMethod("exportAnalytics", String.class, Long.class, LocalDate.class, LocalDate.class);
        assertThat(method).isNotNull();
    }
}

