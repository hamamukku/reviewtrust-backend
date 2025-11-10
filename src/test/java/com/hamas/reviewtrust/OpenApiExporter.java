package com.hamas.reviewtrust;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class OpenApiExporter {
    public static void main(String[] args) throws Exception {
        System.setProperty("spring.profiles.active", "test");
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(ReviewTrustApplication.class)
                .properties(Map.of("server.port", "0"))
                .run();
        try {
            int port = ctx.getEnvironment().getProperty("local.server.port", Integer.class, 8080);
            RestTemplate rest = new RestTemplate();
            String json = rest.getForObject("http://localhost:" + port + "/v3/api-docs", String.class);
            Path target = Path.of("delivery", "openapi.json");
            Files.createDirectories(target.getParent());
            Files.writeString(target, json, StandardCharsets.UTF_8);
            System.out.println("OpenAPI exported to " + target.toAbsolutePath());
        } finally {
            SpringApplication.exit(ctx);
        }
    }
}
