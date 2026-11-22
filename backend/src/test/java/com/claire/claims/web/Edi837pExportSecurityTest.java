package com.claire.claims.web;

import com.claire.claims.dto.Edi837pDtos.Export837pRequest;
import com.claire.claims.dto.Edi837pDtos.ValidateClaimsRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class Edi837pExportSecurityTest {

    @Test
    @DisplayName("export837p requires ADMIN or BILLER role (VIEWER excluded)")
    void testExportSecurity() throws Exception {
        Method method = Edi837pExportController.class.getMethod("export837p", Export837pRequest.class, Jwt.class);
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuth).isNotNull();
        assertThat(preAuth.value()).isEqualTo("hasAnyRole('ADMIN','BILLER')");
    }

    @Test
    @DisplayName("validateClaims requires ADMIN or BILLER role (VIEWER excluded)")
    void testValidateSecurity() throws Exception {
        Method method = Edi837pExportController.class.getMethod("validateClaims", ValidateClaimsRequest.class);
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuth).isNotNull();
        assertThat(preAuth.value()).isEqualTo("hasAnyRole('ADMIN','BILLER')");
    }

    @Test
    @DisplayName("getExports allows ADMIN, BILLER, and VIEWER roles")
    void testGetExportsSecurity() throws Exception {
        Method method = Edi837pExportController.class.getMethod("getExports", Long.class, String.class, Pageable.class);
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuth).isNotNull();
        assertThat(preAuth.value()).isEqualTo("hasAnyRole('ADMIN','BILLER','VIEWER')");
    }
}

