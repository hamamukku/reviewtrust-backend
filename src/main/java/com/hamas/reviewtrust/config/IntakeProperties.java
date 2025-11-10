package com.hamas.reviewtrust.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration for review intake sources.
 */
@ConfigurationProperties(prefix = "intake.review")
public class IntakeProperties {

    /**
     * Comma separated list of directories that contain review NDJSON files.
     */
    private String dirs = "delivery/proof/adhoc,delivery/review-inbox";

    public List<String> dirList() {
        return Arrays.stream(dirs.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    public String getDirs() {
        return dirs;
    }

    public void setDirs(String dirs) {
        this.dirs = dirs;
    }
}

