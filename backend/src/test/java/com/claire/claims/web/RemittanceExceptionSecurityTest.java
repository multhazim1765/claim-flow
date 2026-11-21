package com.claire.claims.web;

import com.claire.claims.dto.RemittanceExceptionDtos.ExceptionResolutionRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class RemittanceExceptionSecurityTest {

    @Test
    @DisplayName("getException allows ADMIN, BILLER, and VIEWER roles")
    void testGetExceptionSecurity() throws Exception {
        Method method = RemittanceExceptionController.class.getMethod("getException", Long.class);
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuth).isNotNull();
        assertThat(preAuth.value()).isEqualTo("hasAnyRole('ADMIN','BILLER','VIEWER')");
    }

    @Test
    @DisplayName("resolveException requires ADMIN or BILLER role (VIEWER excluded)")
    void testResolveExceptionSecurity() throws Exception {
        Method method = RemittanceExceptionController.class.getMethod("resolveException", Long.class, ExceptionResolutionRequest.class);
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuth).isNotNull();
        assertThat(preAuth.value()).isEqualTo("hasAnyRole('ADMIN','BILLER')");
    }
}
