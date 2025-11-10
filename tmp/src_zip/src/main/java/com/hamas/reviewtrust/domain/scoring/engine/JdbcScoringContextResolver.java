package com.hamas.reviewtrust.domain.scoring.engine;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * ScoreService.ScoringContextResolver の既定実装。
 * - あれば review_scores.metrics から特徴量を取得
 * - 無ければ 0 ベースの特徴量で ScoringContext を生成
 * - ScoringContext の生成はプロジェクト依存のため、反射で
 *   of/from/create/builder/4引数コンストラクタ を順に試行する
 */
@Component
public class JdbcScoringContextResolver implements ScoreService.ScoringContextResolver {

    private final JdbcTemplate jdbc;

    public JdbcScoringContextResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ScoringContext resolveByProductId(String productId) {
        // 1) review_scores.metrics から拾う（存在すれば）
        try {
            UUID id = UUID.fromString(productId);
            String sql = """
                SELECT
                  (metrics->>'dist_bias')::float8   AS dist,
                  (metrics->>'duplicates')::float8  AS dup,
                  (metrics->>'surge_z')::float8     AS surge_z,
                  (metrics->>'noise')::float8       AS noise
                FROM public.review_scores
               WHERE product_id = ?
               LIMIT 1
            """;
            Map<String, Object> m = jdbc.queryForMap(sql, id);
            double dist   = getD(m, "dist");
            double dup    = getD(m, "dup");
            double surgeZ = getD(m, "surge_z");
            double noise  = getD(m, "noise");
            ScoringContext ctx = constructContext(dist, dup, surgeZ, noise);
            if (ctx != null) return ctx;
        } catch (Exception ignore) {
            // テーブル未作成/行なし/UUID不正など → フォールバックへ
        }

        // 2) フォールバック：ゼロ特徴量でコンテキスト（起動優先）
        return constructContext(0d, 0d, 0d, 0d);
    }

    private static double getD(Map<String, ?> m, String k) {
        Object v = m.get(k);
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return 0d; }
    }

    /** ScoringContext の生成を反射で総当たり（どれか当たれば採用） */
    private ScoringContext constructContext(double distBias, double duplicateRate, double surgeZ, double noiseRate) {
        try {
            // 1) static factories
            for (String name : new String[]{"of", "from", "create", "ofMetrics"}) {
                try {
                    var m = ScoringContext.class.getMethod(name, double.class, double.class, double.class, double.class);
                    Object o = m.invoke(null, distBias, duplicateRate, surgeZ, noiseRate);
                    return (ScoringContext) o;
                } catch (NoSuchMethodException ignored) {}
            }
            // 2) 4引数コンストラクタ
            try {
                var ctor = ScoringContext.class.getDeclaredConstructor(double.class, double.class, double.class, double.class);
                ctor.setAccessible(true);
                return ctor.newInstance(distBias, duplicateRate, surgeZ, noiseRate);
            } catch (NoSuchMethodException ignored) {}

            // 3) builder() パターン
            try {
                var builderM = ScoringContext.class.getMethod("builder");
                Object b = builderM.invoke(null);
                call(b, "distBias", distBias);
                call(b, "duplicateRate", duplicateRate);
                call(b, "surgeZ", surgeZ);
                call(b, "noiseRate", noiseRate);
                var build = b.getClass().getMethod("build");
                return (ScoringContext) build.invoke(b);
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        // どうしても生成できない場合は null を返す（ScoreService 側が Optional.empty() で握る）
        return null;
    }

    private static void call(Object target, String name, double v) {
        try {
            var m = target.getClass().getMethod(name, double.class);
            m.invoke(target, v);
        } catch (Exception ignored) {}
    }
}
