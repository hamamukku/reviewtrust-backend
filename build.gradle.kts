import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("org.springframework.boot") version "3.3.13"
    id("io.spring.dependency-management") version "1.1.6"
    id("java")
    id("org.flywaydb.flyway") version "9.22.1"
    application                                   // ← ★ 追加（CLI実行用）
}

group = "com.hamas"
version = "0.0.1-SNAPSHOT"
val mainClassName = "com.hamas.reviewtrust.ReviewTrustApplication"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring 基盤
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Security / JWT
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-oauth2-jose")

    // OpenAPI (UI)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    // HTML/Parser
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")

    // Playwright (Java)
    implementation("com.microsoft.playwright:playwright:1.48.0")

    // CSV
    implementation("org.apache.commons:commons-csv:1.11.0")

    // DB / Flyway / Driver
    implementation("org.flywaydb:flyway-core:11.13.3")
    implementation("org.flywaydb:flyway-database-postgresql:11.13.3")
    runtimeOnly("org.postgresql:postgresql:42.7.7")

    // テスト用 H2
    testImplementation("com.h2database:h2:2.2.224")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    // Spring Boot annotation processor (@ConfigurationProperties)
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    exclude("**/ManualTestRunner*", "**/OpenApiExporter*")
    testLogging {
        events("FAILED", "SKIPPED", "STANDARD_OUT", "STANDARD_ERROR")
        exceptionFormat = TestExceptionFormat.FULL
        showCauses = true
        showExceptions = true
        showStackTraces = true
        showStandardStreams = true
    }
}

tasks.register<JavaExec>("playwrightInstall") {
    group = "playwright"
    description = "Download Playwright browsers (Chromium)"
    mainClass.set("com.microsoft.playwright.CLI")
    classpath = sourceSets.main.get().runtimeClasspath
    args("install", "chromium")
}

tasks.register<JavaExec>("amazonReviewScrape") {
    group = "scraping"
    description = "Parse an Amazon detail page and print the review histogram JSON"
    mainClass.set("app.scraper.amazon.AmazonReviewScrapeCli")
    classpath = sourceSets.main.get().runtimeClasspath
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    mainClass.set(mainClassName)
    systemProperty("file.encoding", "UTF-8")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    mainClass.set(mainClassName)
}

// ★ AmazonLoginSeed CLI 実行設定
application {
    // gradlew run で実行されるエントリポイント
    mainClass.set("com.hamas.reviewtrust.tools.AmazonLoginSeed")
}

flyway {
    url = System.getenv("SPRING_DATASOURCE_URL") ?: "jdbc:postgresql://localhost:5432/reviewtrust"
    user = System.getenv("SPRING_DATASOURCE_USERNAME") ?: "postgres"
    password = System.getenv("SPRING_DATASOURCE_PASSWORD") ?: "password"
    locations = arrayOf("filesystem:src/main/resources/db/migration")
}
