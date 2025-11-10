package com.hamas.reviewtrust.domain.audit.repo;

import com.hamas.reviewtrust.domain.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findTop100ByOrderByCreatedAtDesc();
    List<AuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, UUID targetId);
}

