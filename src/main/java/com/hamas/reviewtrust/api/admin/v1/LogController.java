package com.hamas.reviewtrust.api.admin.v1;

import com.hamas.reviewtrust.api.admin.v1.dto.AdminDtos;
import com.hamas.reviewtrust.domain.audit.service.AuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 監査ログ／例外ログの参照API。
 * GET /api/admin/logs/audit?limit=100
 * GET /api/admin/logs/exception?limit=100&include_stack=false
 */
@RestController
@RequestMapping("/api/admin/logs")
public class LogController {

    private final AuditService auditService;

    public LogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/audit")
    public List<AdminDtos.AuditLogDto> audit(@RequestParam(defaultValue = "100") int limit) {
        int n = clamp(limit);
        return auditService.recentAudit().stream()
                .limit(n)
                .map(AdminDtos.AuditLogDto::from)
                .toList();
    }

    @GetMapping("/exception")
    public List<AdminDtos.ExceptionLogDto> exception(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(name = "include_stack", defaultValue = "false") boolean includeStack
    ) {
        int n = clamp(limit);
        return auditService.recentExceptions().stream()
                .limit(n)
                .map(e -> AdminDtos.ExceptionLogDto.from(e, includeStack))
                .toList();
    }

    private int clamp(int limit) {
        if (limit <= 0) return 100;
        return Math.min(limit, 100);
    }
}

