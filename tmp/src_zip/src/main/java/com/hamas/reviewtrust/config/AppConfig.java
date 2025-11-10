// AppConfig.java (placeholder)
package com.hamas.reviewtrust.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.time.Clock;

/**
 * アプリ横断の基本Bean定義。
 * - Clock(UTC)
 * - Jacksonの日付シリアライズ調整
 * - i18nメッセージソース（i18n/messages_ja.properties）
 */
@Configuration
public class AppConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            builder.findModulesViaServiceLoader(true);
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        // クライアント向け根拠文言などは i18n/messages_ja.properties に配置（ツリー準拠）
        ms.setBasenames("i18n/messages_ja");
        ms.setDefaultEncoding("UTF-8");
        ms.setFallbackToSystemLocale(false);
        return ms;
    }
}

