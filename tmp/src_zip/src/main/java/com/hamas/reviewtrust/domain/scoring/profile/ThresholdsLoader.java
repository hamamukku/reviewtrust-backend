// src/main/java/com/hamas/reviewtrust/domain/scoring/profile/ThresholdsLoader.java
package com.hamas.reviewtrust.domain.scoring.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.*;
import java.util.Map;

@Component
public class ThresholdsLoader {
    private static final Logger log = LoggerFactory.getLogger(ThresholdsLoader.class);

    private static final String CLASSPATH = "/scoring/thresholds.yml";
    private static final String PROP_PATH = "scoring.thresholds.path";
    private static final String ENV_PATH  = "SCORING_THRESHOLDS_PATH";

    private volatile Thresholds cached = Thresholds.defaults();
    private volatile Path externalPath = resolveExternal();
    private volatile long lastMtime = -1L;

    public ThresholdsLoader() { tryReload(); }

    /** 取得時に軽くリロード判定（外部ファイルがある場合はmtime監視） */
    public Thresholds get() { tryReload(); return cached; }

    public synchronized void reload(){ loadAndSwap(); }

    private void tryReload() {
        if (externalPath != null) {
            try {
                long mtime = Files.getLastModifiedTime(externalPath, LinkOption.NOFOLLOW_LINKS).toMillis();
                if (mtime != lastMtime) { loadAndSwap(); lastMtime = mtime; }
            } catch (Exception e) {
                log.warn("[ThresholdsLoader] external mtime check failed: {}", externalPath, e);
            }
        } else if (cached == null) {
            loadAndSwap();
        }
    }

    private synchronized void loadAndSwap() {
        try {
            Thresholds th;
            if (externalPath != null && Files.isRegularFile(externalPath)) {
                try (InputStream in = Files.newInputStream(externalPath)) {
                    th = parseYaml(in);
                    log.info("[ThresholdsLoader] loaded from external: {}", externalPath);
                }
            } else {
                try (InputStream in = ThresholdsLoader.class.getResourceAsStream(CLASSPATH)) {
                    if (in == null) {
                        log.warn("[ThresholdsLoader] classpath thresholds.yml not found, use defaults");
                        th = Thresholds.defaults();
                    } else {
                        th = parseYaml(in);
                        log.info("[ThresholdsLoader] loaded from classpath: {}", CLASSPATH);
                    }
                }
            }
            cached = th;
        } catch (Exception e) {
            log.warn("[ThresholdsLoader] load failed, keep previous thresholds", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Thresholds parseYaml(InputStream in){
        var yaml = new Yaml();
        Map<String,Object> map = yaml.load(in);
        if (map == null) return Thresholds.defaults();

        var th = Thresholds.defaults();
        Map<String,Object> weights = (Map<String, Object>) map.get("weights");
        if (weights != null) {
            th.weights.dist_bias = getD(weights,"dist_bias", th.weights.dist_bias);
            th.weights.duplicates= getD(weights,"duplicates", th.weights.duplicates);
            th.weights.surge     = getD(weights,"surge", th.weights.surge);
            th.weights.noise     = getD(weights,"noise", th.weights.noise);
        }
        Map<String,Object> dist = (Map<String, Object>) map.get("dist_bias");
        if (dist != null) { th.dist_bias.warn = getD(dist,"warn", th.dist_bias.warn); th.dist_bias.crit = getD(dist,"crit", th.dist_bias.crit); }
        Map<String,Object> dup  = (Map<String, Object>) map.get("duplicates");
        if (dup  != null) { th.duplicates.warn = getD(dup,"warn", th.duplicates.warn); th.duplicates.crit = getD(dup,"crit", th.duplicates.crit); }
        Map<String,Object> surge= (Map<String, Object>) map.get("surge_z");
        if (surge!= null) { th.surge_z.warn = getD(surge,"warn", th.surge_z.warn); th.surge_z.crit = getD(surge,"crit", th.surge_z.crit); }
        Map<String,Object> noise= (Map<String, Object>) map.get("noise");
        if (noise!= null) { th.noise.warn = getD(noise,"warn", th.noise.warn); th.noise.crit = getD(noise,"crit", th.noise.crit); }

        return th;
    }

    private static double getD(Map<String,Object> m, String k, double def){
        try { Object v = m.get(k); if (v instanceof Number n) return n.doubleValue(); return Double.parseDouble(String.valueOf(v)); }
        catch (Exception ignore) { return def; }
    }

    private static Path resolveExternal() {
        try {
            String p = System.getProperty(PROP_PATH);
            if (p == null || p.isBlank()) p = System.getenv(ENV_PATH);
            if (p == null || p.isBlank()) return null;
            Path path = Paths.get(p);
            return Files.exists(path) ? path : null;
        } catch (Exception e) { return null; }
    }

    // ===== しきい値構造 =====
    public static final class Thresholds {
        public final Weights weights = new Weights();
        public final Band dist_bias  = new Band();
        public final Band duplicates = new Band();
        public final ZBand surge_z   = new ZBand();
        public final Band noise      = new Band();

        public static Thresholds defaults(){
            var t = new Thresholds();
            t.weights.dist_bias = 0.35; t.weights.duplicates = 0.35; t.weights.surge = 0.20; t.weights.noise = 0.10;
            t.dist_bias.warn = 0.35; t.dist_bias.crit = 0.60;
            t.duplicates.warn= 0.30; t.duplicates.crit= 0.50;
            t.surge_z.warn   = 1.80; t.surge_z.crit   = 3.00;
            t.noise.warn     = 0.25; t.noise.crit     = 0.50;
            return t;
        }

        public static final class Weights { public double dist_bias, duplicates, surge, noise; }
        public static class Band { public double warn, crit; }
        public static class ZBand extends Band {}
    }
}
