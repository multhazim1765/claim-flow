package com.claire.claims.web;

import com.claire.claims.dto.ReferenceDtos.*;
import com.claire.claims.dto.UserDtos.CreateUserRequest;
import com.claire.claims.dto.UserDtos.UpdateUserRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class MasterDataSecurityTest {

    @Test
    @DisplayName("Patient mutations require ADMIN or BILLER")
    void testPatientSecurity() throws Exception {
        Method create = PatientController.class.getMethod("create", PatientRequest.class);
        assertThat(create.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAnyRole('ADMIN','BILLER')");

        Method update = PatientController.class.getMethod("update", Long.class, PatientRequest.class);
        assertThat(update.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAnyRole('ADMIN','BILLER')");

        Method status = PatientController.class.getMethod("setStatus", Long.class, boolean.class);
        assertThat(status.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAnyRole('ADMIN','BILLER')");
    }

    @Test
    @DisplayName("Payer mutations require ADMIN role only")
    void testPayerSecurity() throws Exception {
        Method create = PayerController.class.getMethod("create", PayerRequest.class);
        assertThat(create.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");

        Method update = PayerController.class.getMethod("update", Long.class, PayerRequest.class);
        assertThat(update.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");

        Method status = PayerController.class.getMethod("setStatus", Long.class, boolean.class);
        assertThat(status.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
    }

    @Test
    @DisplayName("Provider mutations require ADMIN role only")
    void testProviderSecurity() throws Exception {
        Method create = ProviderController.class.getMethod("create", ProviderRequest.class);
        assertThat(create.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");

        Method update = ProviderController.class.getMethod("update", Long.class, ProviderRequest.class);
        assertThat(update.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");

        Method status = ProviderController.class.getMethod("setStatus", Long.class, boolean.class);
        assertThat(status.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
    }

    @Test
    @DisplayName("Insurance Policy mutations require ADMIN or BILLER")
    void testPolicySecurity() throws Exception {
        Method create = PolicyController.class.getMethod("create", PolicyRequest.class);
        assertThat(create.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAnyRole('ADMIN','BILLER')");

        Method update = PolicyController.class.getMethod("update", Long.class, PolicyRequest.class);
        assertThat(update.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAnyRole('ADMIN','BILLER')");

        Method deactivate = PolicyController.class.getMethod("deactivate", Long.class);
        assertThat(deactivate.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAnyRole('ADMIN','BILLER')");
    }

    @Test
    @DisplayName("UserController is restricted to ADMIN only at class level")
    void testUserSecurity() {
        PreAuthorize classAuth = UserController.class.getAnnotation(PreAuthorize.class);
        assertThat(classAuth).isNotNull();
        assertThat(classAuth.value()).isEqualTo("hasRole('ADMIN')");
    }
}

