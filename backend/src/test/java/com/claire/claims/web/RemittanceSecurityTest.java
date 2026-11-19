package com.claire.claims.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class RemittanceSecurityTest {

    @Test
    @DisplayName("import835 requires ADMIN or BILLER role")
    void testImport835Security() throws Exception {
        Method method = RemittanceController.class.getMethod("import835",
                org.springframework.web.multipart.MultipartFile.class,
                com.claire.claims.dto.RemittanceDtos.ImportRemittanceRequest.class);
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuth).isNotNull();
        assertThat(preAuth.value()).isEqualTo("hasAnyRole('ADMIN','BILLER')");
    }

    @Test
    @DisplayName("list batches requires ADMIN, BILLER, or VIEWER role")
    void testListBatchesSecurity() throws Exception {
        Method method = RemittanceController.class.getMethod("list",
                Long.class,
                com.claire.claims.domain.RemittanceBatchStatus.class,
                org.springframework.data.domain.Pageable.class);
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuth).isNotNull();
        assertThat(preAuth.value()).isEqualTo("hasAnyRole('ADMIN','BILLER','VIEWER')");
    }

    @Test
    @DisplayName("getById requires ADMIN, BILLER, or VIEWER role")
    void testGetByIdSecurity() throws Exception {
        Method method = RemittanceController.class.getMethod("getById", Long.class);
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuth).isNotNull();
        assertThat(preAuth.value()).isEqualTo("hasAnyRole('ADMIN','BILLER','VIEWER')");
    }

    @Test
    @DisplayName("listExceptions requires ADMIN, BILLER, or VIEWER role")
    void testListExceptionsSecurity() throws Exception {
        Method method = RemittanceController.class.getMethod("listExceptions",
                Long.class,
                String.class,
                org.springframework.data.domain.Pageable.class);
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuth).isNotNull();
        assertThat(preAuth.value()).isEqualTo("hasAnyRole('ADMIN','BILLER','VIEWER')");
    }
}

