// RequestIdFilter.java (placeholder)
package com.hamas.reviewtrust.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 相関ID（X-Request-Id）を受け取り/発行して MDC に格納。
 * - 受信ヘッダ優先、無ければ生成（32hex）
 * - レスポンスヘッダに常に反映
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String rid = request.getHeader(HEADER);
        if (rid == null || rid.isBlank()) {
            rid = generate();
        }

        try {
            MDC.put(MDC_KEY, rid);
            response.setHeader(HEADER, rid);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String generate() {
        // 簡易：UUIDをハイフン抜き32桁に
        return UUID.randomUUID().toString().replace("-", "");
    }
}
