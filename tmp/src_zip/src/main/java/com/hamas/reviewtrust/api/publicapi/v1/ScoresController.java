package com.hamas.reviewtrust.api.publicapi.v1;

import com.hamas.reviewtrust.domain.scoring.catalog.ScoreModels.ScoreResult;
import com.hamas.reviewtrust.domain.scoring.engine.ScoreService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/products")
public class ScoresController {

    private final ScoreService scoreService;

    public ScoresController(ScoreService scoreService) {
        this.scoreService = scoreService;
    }

    /**
     * スコア取得:
     *  - 特徴量/レビューが未収集のときは 204 No Content を返す（UIは「データなし」を表示）
     *  - JSONが欲しい場合は ?fallback=empty を付けると 200 + 空ペイロードを返す
     */
    @GetMapping("/{id}/scores")
    public ResponseEntity<?> getScores(
            @PathVariable("id") String productId,
            @RequestParam(value = "fallback", required = false, defaultValue = "none") String fallback
    ) {
        try {
            // サービス層で計算（必要なら内部で再計算やキャッシュを解決）
            Optional<ScoreResult> opt = scoreService.computeForProduct(productId);

            // 特徴量なし/レビュー0件など → 204 or 空ペイロード
            if (opt.isEmpty()) {
                if ("empty".equalsIgnoreCase(fallback)) {
                    return ResponseEntity.ok(Map.of(
                            "productId", productId,
                            "score", 0,
                            "rank", "A",
                            "sakura_judge", "UNKNOWN",
                            "metrics", Map.of(),     // 空
                            "flags", List.of(),      // 空
                            "rules", Map.of(),       // 空
                            "computedAt", Instant.now().toString(),
                            "note", "NO_REVIEWS"
                    ));
                }
                return ResponseEntity.noContent().build(); // 204
            }

            ScoreResult s = opt.get();

            // サービス層が error を同梱してきた場合はそのまま 500 相当に
            if (s.error != null) {
                String code = s.error.getOrDefault("code", "E_INTERNAL");
                String msg  = s.error.getOrDefault("message", "Scoring failed");
                return error(HttpStatus.INTERNAL_SERVER_ERROR, code, msg, null);
            }

            // 正常
            return ResponseEntity.ok(Map.of(
                    "productId",    s.productId,
                    "score",        s.score,
                    "rank",         s.rank,          // A/B/C
                    "sakura_judge", s.sakuraJudge,   // GENUINE/UNLIKELY/LIKELY/SAKURA/UNKNOWN 等
                    "metrics",      s.metrics,
                    "flags",        s.flags,         // ATTN_*
                    "rules",        s.rules,
                    "computedAt",   s.computedAt
            ));

        } catch (IllegalArgumentException e) {
            // 入力不正（存在しないID形式など）
            return error(HttpStatus.BAD_REQUEST, "E_BAD_REQUEST", e.getMessage(), null);
        } catch (Exception e) {
            // 予期せぬ例外は 500（落ちずにJSONで返す）
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "E_INTERNAL", "Unexpected error occurred.", e.getClass().getSimpleName());
        }
    }

    private static ResponseEntity<Map<String,Object>> error(HttpStatus status, String code, String message, String details) {
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of("code", code, "message", message, "details", details)
        ));
    }
}
