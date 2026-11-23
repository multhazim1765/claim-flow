package com.claire.claims.service;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.common.ApiExceptions.ConflictException;
import com.claire.claims.domain.AppUser;
import com.claire.claims.domain.UserRole;
import com.claire.claims.dto.UserDtos.*;
import com.claire.claims.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private AppUserRepository users;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(users, passwordEncoder);
    }

    @Test
    @DisplayName("listUsers returns all users mapped to response DTOs without password hashes")
    void testListUsers() {
        AppUser u = new AppUser();
        u.setId(1L);
        u.setUsername("admin");
        u.setFullName("Avery Admin");
        u.setRole(UserRole.ADMIN);
        u.setEnabled(true);
        u.setCreatedAt(OffsetDateTime.now());

        when(users.findAll()).thenReturn(List.of(u));

        List<UserResponse> list = userService.listUsers();
        assertThat(list).hasSize(1);
        assertThat(list.get(0).username()).isEqualTo("admin");
        assertThat(list.get(0).role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("createUser hashes password and saves new user")
    void testCreateUser() {
        CreateUserRequest req = new CreateUserRequest("newbiller", "secret123", "New Biller", UserRole.BILLER, true);

        when(users.existsByUsername("newbiller")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$10$encodedHash");
        when(users.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });

        UserResponse res = userService.createUser(req);
        assertThat(res.id()).isEqualTo(10L);
        assertThat(res.username()).isEqualTo("newbiller");
        assertThat(res.role()).isEqualTo(UserRole.BILLER);
        assertThat(res.enabled()).isTrue();
    }

    @Test
    @DisplayName("createUser throws ConflictException if username already exists")
    void testCreateUserConflict() {
        CreateUserRequest req = new CreateUserRequest("admin", "secret123", "Admin Copy", UserRole.ADMIN, true);
        when(users.existsByUsername("admin")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already taken");
    }

    @Test
    @DisplayName("setUserStatus prevents deactivating the last active administrator")
    void testPreventDeactivatingLastAdmin() {
        AppUser admin = new AppUser();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setRole(UserRole.ADMIN);
        admin.setEnabled(true);

        when(users.findById(1L)).thenReturn(Optional.of(admin));
        when(users.countByRoleAndEnabledTrue(UserRole.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.setUserStatus(1L, false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot deactivate the last active administrator");
    }

    @Test
    @DisplayName("resetPassword encodes new password and updates user")
    void testResetPassword() {
        AppUser user = new AppUser();
        user.setId(2L);
        user.setUsername("biller");
        user.setPasswordHash("oldHash");

        when(users.findById(2L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPassword123")).thenReturn("newHash");
        when(users.save(any(AppUser.class))).thenReturn(user);

        UserResponse res = userService.resetPassword(2L, new ResetPasswordRequest("newPassword123"));
        assertThat(res.username()).isEqualTo("biller");
        assertThat(user.getPasswordHash()).isEqualTo("newHash");
    }
}

