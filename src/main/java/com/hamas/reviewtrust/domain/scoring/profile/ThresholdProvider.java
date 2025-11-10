package com.hamas.reviewtrust.domain.scoring.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;

/**
 * Loads and persists scoring thresholds from {@code scoring/thresholds.yml}. When an external path
 * is configured via system property or environment variable the provider watches the file for
 * modifications and reloads it on-demand. Otherwise the classpath resource is used.
 */
@Component
public class ThresholdProvider {

    private static final Logger log = LoggerFactory.getLogger(ThresholdProvider.class);
    private static final String CLASSPATH = "/scoring/thresholds.yml";
    private static final String PROP_PATH = "scoring.thresholds.path";
    private static final String ENV_PATH = "SCORING_THRESHOLDS_PATH";

    private final Yaml yaml = new Yaml();
    private final Path externalPath;
    private volatile Thresholds cached = Thresholds.defaults();
    private volatile long lastModified = -1L;

    public ThresholdProvider() {
        this.externalPath = resolveExternal();
        refreshIfNeeded();
    }

    /** Returns the most recent thresholds, reloading if the source file changed. */
    public Thresholds get() {
        refreshIfNeeded();
        return cached;
    }

    /** Force a reload regardless of timestamps. */
    public synchronized void reload() {
        loadAndCache();
    }

    /**
     * Persist thresholds to the configured external path. When no external path is set the
     * invocation is ignored because classpath resources are read-only in packaged deployments.
     */
    public synchronized void save(Thresholds thresholds) {
        Objects.requireNonNull(thresholds, "thresholds");
        if (externalPath == null) {
            log.warn("[ThresholdProvider] no external thresholds path configured; skipping save");
            return;
        }
        try {
            Files.createDirectories(externalPath.getParent());
            try (OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(externalPath))) {
                yaml.dump(thresholds.toMap(), writer);
            }
            lastModified = Files.getLastModifiedTime(externalPath, LinkOption.NOFOLLOW_LINKS).toMillis();
            cached = thresholds;
            log.info("[ThresholdProvider] thresholds persisted to {}", externalPath);
        } catch (IOException e) {
            log.error("[ThresholdProvider] failed to persist thresholds.yml", e);
            throw new IllegalStateException("Unable to write thresholds", e);
        }
    }

    private void refreshIfNeeded() {
        if (externalPath != null) {
            try {
                long current = Files.getLastModifiedTime(externalPath, LinkOption.NOFOLLOW_LINKS).toMillis();
                if (current != lastModified) {
                    loadAndCache();
                    lastModified = current;
                }
            } catch (IOException e) {
                log.warn("[ThresholdProvider] unable to inspect external thresholds: {}", externalPath, e);
            }
        } else if (cached == null) {
            loadAndCache();
        }
    }

    private synchronized void loadAndCache() {
        try {
            Thresholds thresholds;
            if (externalPath != null && Files.isRegularFile(externalPath)) {
                try (InputStream in = Files.newInputStream(externalPath)) {
                    thresholds = parse(in);
                    log.info("[ThresholdProvider] loaded thresholds from {}", externalPath);
                }
            } else {
                try (InputStream in = ThresholdProvider.class.getResourceAsStream(CLASSPATH)) {
                    if (in == null) {
                        log.warn("[ThresholdProvider] classpath thresholds missing; using defaults");
                        thresholds = Thresholds.defaults();
                    } else {
                        thresholds = parse(in);
                        log.info("[ThresholdProvider] loaded thresholds from classpath {}", CLASSPATH);
                    }
                }
            }
            cached = thresholds;
        } catch (Exception e) {
            log.warn("[ThresholdProvider] failed to load thresholds; keeping previous values", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Thresholds parse(InputStream in) {
        Map<String, Object> raw = yaml.load(in);
        if (raw == null) return Thresholds.defaults();

        Thresholds thresholds = Thresholds.defaults();
        Map<String, Object> weights = (Map<String, Object>) raw.get("weights");
        if (weights != null) {
            thresholds.weights.dist_bias = getDouble(weights, "dist_bias", thresholds.weights.dist_bias);
            thresholds.weights.duplicates = getDouble(weights, "duplicates", thresholds.weights.duplicates);
            thresholds.weights.surge = getDouble(weights, "surge", thresholds.weights.surge);
            thresholds.weights.noise = getDouble(weights, "noise", thresholds.weights.noise);
        }
        Map<String, Object> dist = (Map<String, Object>) raw.get("dist_bias");
        if (dist != null) {
            thresholds.dist_bias.warn = getDouble(dist, "warn", thresholds.dist_bias.warn);
            thresholds.dist_bias.crit = getDouble(dist, "crit", thresholds.dist_bias.crit);
        }
        Map<String, Object> dup = (Map<String, Object>) raw.get("duplicates");
        if (dup != null) {
            thresholds.duplicates.warn = getDouble(dup, "warn", thresholds.duplicates.warn);
            thresholds.duplicates.crit = getDouble(dup, "crit", thresholds.duplicates.crit);
        }
        Map<String, Object> surge = (Map<String, Object>) raw.get("surge_z");
        if (surge != null) {
            thresholds.surge_z.warn = getDouble(surge, "warn", thresholds.surge_z.warn);
            thresholds.surge_z.crit = getDouble(surge, "crit", thresholds.surge_z.crit);
        }
        Map<String, Object> noise = (Map<String, Object>) raw.get("noise");
        if (noise != null) {
            thresholds.noise.warn = getDouble(noise, "warn", thresholds.noise.warn);
            thresholds.noise.crit = getDouble(noise, "crit", thresholds.noise.crit);
        }
        Map<String, Object> sakura = (Map<String, Object>) raw.get("sakura_judge");
        if (sakura != null) {
            thresholds.sakura.dist_bias_sakura = getDouble(sakura, "dist_bias_sakura", thresholds.sakura.dist_bias_sakura);
            thresholds.sakura.dist_bias_likely = getDouble(sakura, "dist_bias_likely", thresholds.sakura.dist_bias_likely);
            thresholds.sakura.dist_bias_unlikely = getDouble(sakura, "dist_bias_unlikely", thresholds.sakura.dist_bias_unlikely);
            thresholds.sakura.duplicate_sakura = getDouble(sakura, "duplicate_sakura", thresholds.sakura.duplicate_sakura);
            thresholds.sakura.duplicate_likely = getDouble(sakura, "duplicate_likely", thresholds.sakura.duplicate_likely);
        }

        Map<String, Object> featurePercent = (Map<String, Object>) raw.get("feature_percent");
        if (featurePercent != null) {
            thresholds.featurePercent.dist_bias.warn = getNestedDouble(featurePercent, "dist_bias", "warn", thresholds.featurePercent.dist_bias.warn);
            thresholds.featurePercent.dist_bias.crit = getNestedDouble(featurePercent, "dist_bias", "crit", thresholds.featurePercent.dist_bias.crit);
            thresholds.featurePercent.duplicates.warn = getNestedDouble(featurePercent, "duplicates", "warn", thresholds.featurePercent.duplicates.warn);
            thresholds.featurePercent.duplicates.crit = getNestedDouble(featurePercent, "duplicates", "crit", thresholds.featurePercent.duplicates.crit);
            thresholds.featurePercent.surge.warn = getNestedDouble(featurePercent, "surge", "warn", thresholds.featurePercent.surge.warn);
            thresholds.featurePercent.surge.crit = getNestedDouble(featurePercent, "surge", "crit", thresholds.featurePercent.surge.crit);
            thresholds.featurePercent.noise.warn = getNestedDouble(featurePercent, "noise", "warn", thresholds.featurePercent.noise.warn);
            thresholds.featurePercent.noise.crit = getNestedDouble(featurePercent, "noise", "crit", thresholds.featurePercent.noise.crit);
        }

        Map<String, Object> sakuraPercent = (Map<String, Object>) raw.get("sakura_percent");
        if (sakuraPercent != null) {
            thresholds.sakuraPercent.sakura.dist_bias = getNestedDouble(sakuraPercent, "sakura", "dist_bias", thresholds.sakuraPercent.sakura.dist_bias);
            thresholds.sakuraPercent.sakura.duplicates = getNestedDouble(sakuraPercent, "sakura", "duplicates", thresholds.sakuraPercent.sakura.duplicates);
            thresholds.sakuraPercent.likely.dist_bias = getNestedDouble(sakuraPercent, "likely", "dist_bias", thresholds.sakuraPercent.likely.dist_bias);
            thresholds.sakuraPercent.likely.duplicates = getNestedDouble(sakuraPercent, "likely", "duplicates", thresholds.sakuraPercent.likely.duplicates);
            thresholds.sakuraPercent.unlikely.dist_bias = getNestedDouble(sakuraPercent, "unlikely", "dist_bias", thresholds.sakuraPercent.unlikely.dist_bias);
            thresholds.sakuraPercent.unlikely.duplicates = getNestedDouble(sakuraPercent, "unlikely", "duplicates", thresholds.sakuraPercent.unlikely.duplicates);
        }
        return thresholds;
    }

    @SuppressWarnings("unchecked")
    private static double getNestedDouble(Map<String, Object> parent, String section, String key, double defaultValue) {
        Object nested = parent.get(section);
        if (nested instanceof Map<?, ?> nestedMap) {
            return getDouble((Map<String, Object>) nestedMap, key, defaultValue);
        }
        return defaultValue;
    }

    private static double getDouble(Map<String, Object> map, String key, double defaultValue) {
        try {
            Object value = map.get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value != null) {
                return Double.parseDouble(String.valueOf(value));
            }
        } catch (Exception ignored) {
        }
        return defaultValue;
    }

    private static Path resolveExternal() {
        try {
            String path = System.getProperty(PROP_PATH);
            if (path == null || path.isBlank()) {
                path = System.getenv(ENV_PATH);
            }
            if (path == null || path.isBlank()) return null;
            return Paths.get(path);
        } catch (Exception e) {
            return null;
        }
    }

    // ===== Data structures =====
    public static final class Thresholds {
        public final Weights weights = new Weights();
        public final Band dist_bias = new Band();
        public final Band duplicates = new Band();
        public final ZBand surge_z = new ZBand();
        public final Band noise = new Band();
        public final Sakura sakura = new Sakura();
        public final FeaturePercent featurePercent = new FeaturePercent();
        public final SakuraPercent sakuraPercent = new SakuraPercent();

        public static Thresholds defaults() {
            Thresholds t = new Thresholds();
            t.weights.dist_bias = 0.35;
            t.weights.duplicates = 0.35;
            t.weights.surge = 0.20;
            t.weights.noise = 0.10;
            t.dist_bias.warn = 0.35;
            t.dist_bias.crit = 0.60;
            t.duplicates.warn = 0.30;
            t.duplicates.crit = 0.50;
            t.surge_z.warn = 1.80;
            t.surge_z.crit = 3.00;
            t.noise.warn = 0.25;
            t.noise.crit = 0.50;
            t.sakura.dist_bias_sakura = 0.80;
            t.sakura.dist_bias_likely = 0.65;
            t.sakura.dist_bias_unlikely = 0.45;
            t.sakura.duplicate_sakura = 0.50;
            t.sakura.duplicate_likely = 0.40;
            t.featurePercent.dist_bias.warn = 65;
            t.featurePercent.dist_bias.crit = 80;
            t.featurePercent.duplicates.warn = 40;
            t.featurePercent.duplicates.crit = 55;
            t.featurePercent.surge.warn = 40;
            t.featurePercent.surge.crit = 65;
            t.featurePercent.noise.warn = 35;
            t.featurePercent.noise.crit = 55;
            t.sakuraPercent.sakura.dist_bias = 80;
            t.sakuraPercent.sakura.duplicates = 50;
            t.sakuraPercent.likely.dist_bias = 65;
            t.sakuraPercent.likely.duplicates = 40;
            t.sakuraPercent.unlikely.dist_bias = 45;
            t.sakuraPercent.unlikely.duplicates = 0;
            return t;
        }

        public Map<String, Object> toMap() {
            return Map.of(
                    "weights", Map.of(
                            "dist_bias", weights.dist_bias,
                            "duplicates", weights.duplicates,
                            "surge", weights.surge,
                            "noise", weights.noise
                    ),
                    "dist_bias", Map.of("warn", dist_bias.warn, "crit", dist_bias.crit),
                    "duplicates", Map.of("warn", duplicates.warn, "crit", duplicates.crit),
                    "surge_z", Map.of("warn", surge_z.warn, "crit", surge_z.crit),
                    "noise", Map.of("warn", noise.warn, "crit", noise.crit),
                    "sakura_judge", Map.of(
                            "dist_bias_sakura", sakura.dist_bias_sakura,
                            "dist_bias_likely", sakura.dist_bias_likely,
                            "dist_bias_unlikely", sakura.dist_bias_unlikely,
                            "duplicate_sakura", sakura.duplicate_sakura,
                            "duplicate_likely", sakura.duplicate_likely
                    ),
                    "feature_percent", Map.of(
                            "dist_bias", Map.of("warn", featurePercent.dist_bias.warn, "crit", featurePercent.dist_bias.crit),
                            "duplicates", Map.of("warn", featurePercent.duplicates.warn, "crit", featurePercent.duplicates.crit),
                            "surge", Map.of("warn", featurePercent.surge.warn, "crit", featurePercent.surge.crit),
                            "noise", Map.of("warn", featurePercent.noise.warn, "crit", featurePercent.noise.crit)
                    ),
                    "sakura_percent", Map.of(
                            "sakura", Map.of("dist_bias", sakuraPercent.sakura.dist_bias, "duplicates", sakuraPercent.sakura.duplicates),
                            "likely", Map.of("dist_bias", sakuraPercent.likely.dist_bias, "duplicates", sakuraPercent.likely.duplicates),
                            "unlikely", Map.of("dist_bias", sakuraPercent.unlikely.dist_bias, "duplicates", sakuraPercent.unlikely.duplicates)
                    )
            );
        }

        public static final class Weights {
            public double dist_bias;
            public double duplicates;
            public double surge;
            public double noise;
        }

        public static class Band {
            public double warn;
            public double crit;
        }

        public static class ZBand extends Band {
        }

        public static class Sakura {
            public double dist_bias_sakura;
            public double dist_bias_likely;
            public double dist_bias_unlikely;
            public double duplicate_sakura;
            public double duplicate_likely;
        }

        public static final class FeaturePercent {
            public final Band dist_bias = new Band();
            public final Band duplicates = new Band();
            public final Band surge = new Band();
            public final Band noise = new Band();
        }

        public static final class SakuraPercent {
            public final PercentBand sakura = new PercentBand();
            public final PercentBand likely = new PercentBand();
            public final PercentBand unlikely = new PercentBand();
        }

        public static class PercentBand {
            public double dist_bias;
            public double duplicates;
        }
    }
}
