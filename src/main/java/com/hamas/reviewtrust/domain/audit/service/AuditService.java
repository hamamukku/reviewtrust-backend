// AuditService.java (placeholder)
package com.hamas.reviewtrust.domain.audit.service;

import com.hamas.reviewtrust.domain.audit.entity.AuditLog;
import com.hamas.reviewtrust.domain.audit.entity.ExceptionLog;
import com.hamas.reviewtrust.domain.audit.repo.AuditLogRepository;
import com.hamas.reviewtrust.domain.audit.repo.ExceptionLogRepository;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogRepository auditRepo;
    private final ExceptionLogRepository exRepo;

    public AuditService(AuditLogRepository auditRepo, ExceptionLogRepository exRepo) {
        this.auditRepo = auditRepo;
        this.exRepo = exRepo;
    }

    /** 変更ログの記録（承認/非承認・表示切替・再取得など） */
    @Transactional
    public AuditLog recordAction(UUID actorId, String action, String targetType, UUID targetId, String metaJson) {
        return auditRepo.save(AuditLog.of(actorId, action, targetType, targetId, metaJson));
    }

    /** 例外ログの記録（任意メッセージ・スタックトレース付き） */
    @Transactional
    public ExceptionLog recordException(String scope, String errorCode, String message, String stack, UUID jobId) {
        return exRepo.save(ExceptionLog.of(scope, errorCode, messageWithRid(message), stack, jobId));
    }

    /** 例外ログの記録（Throwable から自動整形） */
    @Transactional
    public ExceptionLog recordException(String scope, String errorCode, Throwable t, UUID jobId) {
        String msg = (t.getMessage() != null) ? t.getMessage() : t.getClass().getName();
        return exRepo.save(ExceptionLog.of(scope, errorCode, messageWithRid(msg), toStackTrace(t), jobId));
    }

    /** 直近の監査ログ（最大100件） */
    @Transactional(readOnly = true)
    public List<AuditLog> recentAudit() {
        return auditRepo.findTop100ByOrderByCreatedAtDesc();
    }

    /** 直近の例外ログ（最大100件） */
    @Transactional(readOnly = true)
    public List<ExceptionLog> recentExceptions() {
        return exRepo.findTop100ByOrderByCreatedAtDesc();
    }

    private String messageWithRid(String msg) {
        String rid = MDC.get("requestId");
        return (rid == null || rid.isBlank()) ? msg : "[rid=" + rid + "] " + msg;
    }

    private String toStackTrace(Throwable t) {
        StringWriter sw = new StringWriter(4096);
        t.printStackTrace(new PrintWriter(sw));
        String s = sw.toString();
        return s.length() > 20000 ? s.substring(0, 20000) : s; // 過剰肥大の抑制
    }
}
