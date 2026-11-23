package com.claire.claims.dto;

import com.claire.claims.domain.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public final class UserDtos {

    private UserDtos() { }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 60) String username,
            @NotBlank @Size(min = 6, max = 100) String password,
            @NotBlank @Size(max = 120) String fullName,
            @NotNull UserRole role,
            Boolean enabled) { }

    public record UpdateUserRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotNull UserRole role,
            Boolean enabled) { }

    public record UserResponse(
            Long id,
            String username,
            String fullName,
            UserRole role,
            boolean enabled,
            OffsetDateTime createdAt) { }

    public record ResetPasswordRequest(
            @NotBlank @Size(min = 6, max = 100) String newPassword) { }

    public record UserStatusRequest(
            boolean enabled) { }
}

