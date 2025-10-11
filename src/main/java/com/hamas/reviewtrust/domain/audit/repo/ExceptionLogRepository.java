// ExceptionLogRepository.java (placeholder)
package com.hamas.reviewtrust.domain.audit.repo;

import com.hamas.reviewtrust.domain.audit.entity.ExceptionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExceptionLogRepository extends JpaRepository<ExceptionLog, UUID> {
    List<ExceptionLog> findTop100ByOrderByCreatedAtDesc();
    List<ExceptionLog> findByScopeOrderByCreatedAtDesc(String scope);
}
