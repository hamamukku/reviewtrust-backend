plugins {
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
    id("java")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

repositories { mavenCentral() }

dependencies {
    // Spring 基本
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // ★不足していた依存（これでコンパイルエラーが消える）
    implementation("org.springframework.boot:spring-boot-starter-security")          // Security
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")       // OpenAPI(Swagger UI)

    // HTMLパース
    implementation("org.jsoup:jsoup:1.16.1")

    // DB / マイグレーション
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test { useJUnitPlatform() }
