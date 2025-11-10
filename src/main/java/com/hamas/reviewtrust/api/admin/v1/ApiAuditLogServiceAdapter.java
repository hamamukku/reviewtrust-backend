// src/main/java/com/hamas/reviewtrust/api/admin/v1/ApiAuditLogServiceAdapter.java
package com.hamas.reviewtrust.api.admin.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 監査ログアダプタ（products/reviews 両Controllerの内側IFを実装）。
 * テーブル audit_logs が無い/古い場合は握りつぶしてアプリを落とさない。
 */
@Component
public class ApiAuditLogServiceAdapter
        implements AdminProductsController.AuditLogService,
                   AdminReviewsController.AuditLogService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper om = new ObjectMapper();

    public ApiAuditLogServiceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void write(String action, Map<String, ?> payload) {
        try {
            String json = om.writeValueAsString(payload != null ? payload : Map.of());
            String reqId = MDC.get("req.id");
            // actor / entity_* は必要になったら拡張。まずは action と payload だけ確実に保存。
            jdbc.update(
                "INSERT INTO public.audit_logs(ts, actor, action, entity_type, entity_id, request_id, payload) " +
                "VALUES (now(), ?, ?, ?, ?, ?, cast(? as jsonb))",
                null,                // actor（後で SecurityContext から入れてOK）
                action,
                null,                // entity_type
                null,                // entity_id
                reqId,
                json
            );
        } catch (Exception ignore) {
            // ログ系は本体処理の邪魔をしない
        }
    }
}
