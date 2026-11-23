package com.claire.claims.service;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.common.ApiExceptions.ConflictException;
import com.claire.claims.common.ApiExceptions.NotFoundException;
import com.claire.claims.domain.AppUser;
import com.claire.claims.domain.UserRole;
import com.claire.claims.dto.UserDtos.*;
import com.claire.claims.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return users.findAll().stream()
                .sorted((a, b) -> a.getUsername().compareToIgnoreCase(b.getUsername()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        return toResponse(users.findById(id).orElseThrow(() -> new NotFoundException("User", id)));
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (users.existsByUsername(request.username())) {
            throw new ConflictException("Username " + request.username() + " is already taken");
        }
        AppUser user = new AppUser();
        user.setUsername(request.username().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        user.setEnabled(request.enabled() != null ? request.enabled() : true);
        return toResponse(users.save(user));
    }

    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        AppUser user = users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
        boolean willBeActiveAdmin = (request.enabled() != null ? request.enabled() : user.isEnabled())
                && request.role() == UserRole.ADMIN;
        if (user.getRole() == UserRole.ADMIN && user.isEnabled() && !willBeActiveAdmin) {
            long activeAdmins = users.countByRoleAndEnabledTrue(UserRole.ADMIN);
            if (activeAdmins <= 1) {
                throw new BusinessRuleException("Cannot deactivate or demote the last active administrator");
            }
        }

        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        return toResponse(users.save(user));
    }

    @Transactional
    public UserResponse setUserStatus(Long id, boolean enabled) {
        AppUser user = users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
        if (user.getRole() == UserRole.ADMIN && user.isEnabled() && !enabled) {
            long activeAdmins = users.countByRoleAndEnabledTrue(UserRole.ADMIN);
            if (activeAdmins <= 1) {
                throw new BusinessRuleException("Cannot deactivate the last active administrator");
            }
        }
        user.setEnabled(enabled);
        return toResponse(users.save(user));
    }

    @Transactional
    public UserResponse resetPassword(Long id, ResetPasswordRequest request) {
        AppUser user = users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        return toResponse(users.save(user));
    }

    private UserResponse toResponse(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt());
    }
}

