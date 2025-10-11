// SwaggerConfig.java (placeholder)
package com.hamas.reviewtrust.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc-openapi の最小設定。
 * - /swagger-ui, /v3/api-docs を公開（Security側で許可済み）
 * - 管理API用に Basic 認証スキームをスキーマ定義（要件上は固定1ユーザー）
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI reviewTrustOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ReviewTrust API")
                        .version("v1")
                        .description("レビュー信頼性の可視化（MVP）")
                        .contact(new Contact().name("ReviewTrust"))
                )
                .components(new Components()
                        .addSecuritySchemes("basicAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("basic")));
    }

    /** 公開APIグループ（/api/** から管理系を除外） */
    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch("/api/**")
                .pathsToExclude("/api/admin/**")
                .build();
    }

    /** 管理APIグループ（/api/admin/**） */
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .pathsToMatch("/api/admin/**")
                .build();
    }
}
