package com.claire.claims.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentPostingSecurityTest {

    @Test
    @DisplayName("postVoucher requires ADMIN or BILLER role (VIEWER excluded)")
    void testPostVoucherSecurity() throws Exception {
        Method method = PaymentPostingController.class.getMethod("postVoucher", Long.class);
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuth).isNotNull();
        assertThat(preAuth.value()).isEqualTo("hasAnyRole('ADMIN','BILLER')");
    }

    @Test
    @DisplayName("postBatch requires ADMIN or BILLER role (VIEWER excluded)")
    void testPostBatchSecurity() throws Exception {
        Method method = PaymentPostingController.class.getMethod("postBatch", Long.class);
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuth).isNotNull();
        assertThat(preAuth.value()).isEqualTo("hasAnyRole('ADMIN','BILLER')");
    }

    @Test
    @DisplayName("getClaimLedger allows ADMIN, BILLER, and VIEWER roles")
    void testGetClaimLedgerSecurity() throws Exception {
        Method method = PaymentPostingController.class.getMethod("getClaimLedger", Long.class);
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuth).isNotNull();
        assertThat(preAuth.value()).isEqualTo("hasAnyRole('ADMIN','BILLER','VIEWER')");
    }
}

