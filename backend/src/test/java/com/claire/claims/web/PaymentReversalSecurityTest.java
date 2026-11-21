package com.claire.claims.web;

import com.claire.claims.dto.PaymentReversalDtos.PaymentReversalRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentReversalSecurityTest {

    @Test
    @DisplayName("reversePayment requires ADMIN or BILLER role (VIEWER excluded)")
    void testReversePaymentSecurity() throws Exception {
        Method method = PaymentReversalController.class.getMethod("reversePayment", Long.class, PaymentReversalRequest.class);
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuth).isNotNull();
        assertThat(preAuth.value()).isEqualTo("hasAnyRole('ADMIN','BILLER')");
    }
}

