package com.hamas.reviewtrust.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

@ConfigurationProperties(prefix = "intake.local")
public class LocalIntakeProperties {

    private Path csvPath = Paths.get("data/urls.csv");
    private String remoteBaseUrl;
    private String remoteAuthToken;

    public Path getCsvPath() {
        return csvPath;
    }

    public void setCsvPath(Path csvPath) {
        this.csvPath = csvPath;
    }

    public String getRemoteBaseUrl() {
        return remoteBaseUrl;
    }

    public void setRemoteBaseUrl(String remoteBaseUrl) {
        this.remoteBaseUrl = remoteBaseUrl;
    }

    public String getRemoteAuthToken() {
        return remoteAuthToken;
    }

    public void setRemoteAuthToken(String remoteAuthToken) {
        this.remoteAuthToken = remoteAuthToken;
    }
}

