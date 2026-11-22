package com.claire.claims.web;

import com.claire.claims.dto.ClaimAttachmentDtos.ArchiveAttachmentRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimAttachmentSecurityTest {

    @Test
    @DisplayName("uploadAttachment requires ADMIN or BILLER role (VIEWER excluded)")
    void testUploadAttachmentSecurity() throws Exception {
        Method method = ClaimAttachmentController.class.getMethod(
                "uploadAttachment",
                Long.class,
                MultipartFile.class,
                String.class,
                String.class,
                Jwt.class
        );
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuth).isNotNull();
        assertThat(preAuth.value()).isEqualTo("hasAnyRole('ADMIN','BILLER')");
    }

    @Test
    @DisplayName("downloadAttachment requires ADMIN or BILLER role (VIEWER restricted by default)")
    void testDownloadAttachmentSecurity() throws Exception {
        Method method = ClaimAttachmentController.class.getMethod(
                "downloadAttachment",
                Long.class,
                Long.class,
                Jwt.class
        );
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuth).isNotNull();
        assertThat(preAuth.value()).isEqualTo("hasAnyRole('ADMIN','BILLER')");
    }

    @Test
    @DisplayName("archiveAttachment requires ADMIN or BILLER role (VIEWER excluded)")
    void testArchiveAttachmentSecurity() throws Exception {
        Method method = ClaimAttachmentController.class.getMethod(
                "archiveAttachment",
                Long.class,
                Long.class,
                ArchiveAttachmentRequest.class,
                Jwt.class
        );
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuth).isNotNull();
        assertThat(preAuth.value()).isEqualTo("hasAnyRole('ADMIN','BILLER')");
    }

    @Test
    @DisplayName("listAttachments allows ADMIN, BILLER, and VIEWER roles")
    void testListAttachmentsSecurity() throws Exception {
        Method method = ClaimAttachmentController.class.getMethod("listAttachments", Long.class);
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuth).isNotNull();
        assertThat(preAuth.value()).isEqualTo("hasAnyRole('ADMIN','BILLER','VIEWER')");
    }

    @Test
    @DisplayName("searchAttachments allows ADMIN, BILLER, and VIEWER roles")
    void testSearchAttachmentsSecurity() throws Exception {
        Method method = ClaimAttachmentController.class.getMethod("searchAttachments", String.class, String.class, String.class, Pageable.class);
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuth).isNotNull();
        assertThat(preAuth.value()).isEqualTo("hasAnyRole('ADMIN','BILLER','VIEWER')");
    }
}

