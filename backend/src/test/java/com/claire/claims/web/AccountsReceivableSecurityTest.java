package com.claire.claims.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AccountsReceivableSecurityTest {

    @Test
    @DisplayName("getArSummary allows ADMIN, BILLER, and VIEWER roles")
    void testGetArSummarySecurity() throws Exception {
        Method method = AccountsReceivableController.class.getMethod("getArSummary");
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuth).isNotNull();
        assertThat(preAuth.value()).isEqualTo("hasAnyRole('ADMIN','BILLER','VIEWER')");
    }

    @Test
    @DisplayName("getClaimArBreakdown allows ADMIN, BILLER, and VIEWER roles")
    void testGetClaimArBreakdownSecurity() throws Exception {
        Method method = AccountsReceivableController.class.getMethod("getClaimArBreakdown", Long.class);
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuth).isNotNull();
        assertThat(preAuth.value()).isEqualTo("hasAnyRole('ADMIN','BILLER','VIEWER')");
    }
}

