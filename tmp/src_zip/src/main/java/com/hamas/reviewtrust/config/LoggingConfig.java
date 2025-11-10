// LoggingConfig.java — 完全修正版（差し替え可）
package com.hamas.reviewtrust.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * ログ出力の最小構成:
 * - HTTPメソッド/パス/ステータス/所要時間を1行ログ化
 * - RequestIdFilter が入れた MDC("req.id") を活用（ここでは触れない）
 * - TaskDecorator により @Async やスケジューラでも MDC を引き継ぐ
 *
 * logback-spring.xml 側で JSON レイアウト＆ %X{req.id} / %X{method} などを参照する想定。
 */
@Configuration
public class LoggingConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HttpLoggingInterceptor()).addPathPatterns("/**");
    }

    /** HTTPアクセスログ用の最小Interceptor（MDC追加＆所要時間計測） */
    static final class HttpLoggingInterceptor implements HandlerInterceptor {
        private static final Logger log = LoggerFactory.getLogger("http");

        @Override
        public boolean preHandle(@NonNull HttpServletRequest req,
                                 @NonNull HttpServletResponse res,
                                 @NonNull Object handler) throws Exception {
            req.setAttribute("_rtStart", System.nanoTime());
            MDC.put("method", req.getMethod());
            MDC.put("path", req.getRequestURI());
            return true;
        }

        @Override
        public void afterCompletion(@NonNull HttpServletRequest req,
                                    @NonNull HttpServletResponse res,
                                    @NonNull Object handler,
                                    Exception ex) throws Exception {
            Object startObj = req.getAttribute("_rtStart");
            long durMs = (startObj instanceof Long s)
                    ? TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - s)
                    : -1L;

            String user = Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                    .filter(Authentication::isAuthenticated)
                    .map(Authentication::getName)
                    .orElse("-");

            MDC.put("status", String.valueOf(res.getStatus()));
            MDC.put("durationMs", String.valueOf(durMs));
            MDC.put("user", user);

            if (ex != null) {
                log.warn("HTTP {} {} -> {} ({} ms)", req.getMethod(), req.getRequestURI(), res.getStatus(), durMs, ex);
            } else {
                log.info("HTTP {} {} -> {} ({} ms)", req.getMethod(), req.getRequestURI(), res.getStatus(), durMs);
            }

            // 後片付け（req.id は RequestIdFilter 側で管理）
            MDC.remove("method");
            MDC.remove("path");
            MDC.remove("status");
            MDC.remove("durationMs");
            MDC.remove("user");
        }
    }

    /**
     * 非同期/スケジューラで MDC を引き継ぐための TaskDecorator。
     * ThreadPoolTaskExecutor や TaskScheduler にセットして利用。
     */
    @Bean
    public TaskDecorator mdcTaskDecorator() {
        return runnable -> {
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                if (contextMap != null) MDC.setContextMap(contextMap);
                try {
                    runnable.run();
                } finally {
                    if (previous != null) MDC.setContextMap(previous);
                    else MDC.clear();
                }
            };
        };
    }
}
