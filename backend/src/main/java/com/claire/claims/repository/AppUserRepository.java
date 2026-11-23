package com.claire.claims.repository;

import com.claire.claims.domain.AppUser;
import com.claire.claims.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    boolean existsByUsername(String username);
    long countByRoleAndEnabledTrue(UserRole role);
}
