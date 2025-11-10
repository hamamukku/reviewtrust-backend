
// src/main/java/com/hamas/reviewtrust/domain/accounts/repo/AdminUserRepository.java
package com.hamas.reviewtrust.domain.accounts.repo;

import com.hamas.reviewtrust.domain.accounts.entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {
    Optional<AdminUser> findByUsername(String username);
    Optional<AdminUser> findByEmail(String email);
    Optional<AdminUser> findByUsernameOrEmail(String username, String email);
}
