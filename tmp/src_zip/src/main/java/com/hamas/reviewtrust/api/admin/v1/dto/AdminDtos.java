// AdminDtos.java (placeholder)
package com.hamas.reviewtrust.api.admin.v1.dto;

import com.hamas.reviewtrust.domain.audit.entity.AuditLog;
import com.hamas.reviewtrust.domain.audit.entity.ExceptionLog;

import java.time.Instant;
import java.util.UUID;

/** 管理API向けの簡易DTO。エンティティをそのまま晒さず必要最小限に整形。 */
public final class AdminDtos {

    /** 変更ログの表示用DTO（metaはJSON文字列のまま返す） */
    public record AuditLogDto(
            UUID id,
            UUID actorId,
            String action,
            String targetType,
            UUID targetId,
            String meta,           // metaJson をそのまま
            Instant createdAt
    ) {
        public static AuditLogDto from(AuditLog a) {
            return new AuditLogDto(
                    a.getId(), a.getActorId(), a.getAction(),
                    a.getTargetType(), a.getTargetId(),
                    a.getMetaJson(), a.getCreatedAt()
            );
        }
    }

    /** 例外ログの表示用DTO（stackは既定で非表示、クエリで切替） */
    public record ExceptionLogDto(
            UUID id,
            UUID jobId,
            String scope,
            String errorCode,
            String message,
            String stack,          // include_stack=true のときのみ詰める
            Instant createdAt
    ) {
        public static ExceptionLogDto from(ExceptionLog e, boolean includeStack) {
            return new ExceptionLogDto(
                    e.getId(), e.getJobId(), e.getScope(),
                    e.getErrorCode(), e.getMessage(),
                    includeStack ? e.getStack() : null,
                    e.getCreatedAt()
            );
        }
    }

    private AdminDtos() { }
}

