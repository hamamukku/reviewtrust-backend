// RequestIdFilter.java — 完全修正版（差し替え可）
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
import java.util.regex.Pattern;

/**
 * 相関ID（X-Request-Id）を受け取り/発行して MDC に格納するフィルタ。
 *
 * 仕様:
 * - 受信ヘッダ X-Request-Id を優先採用。未指定または不正値なら生成（32hex）。
 * - （保険）クエリ param "requestId" も許容。ただし不正値は無視。
 * - レスポンスにも必ず X-Request-Id を反映（例外時でも追跡可能）。
 * - ログの MDC には "req.id" として格納（logback の %X{req.id} で参照）。
 *
 * セキュリティ:
 * - 許容文字は [A-Za-z0-9._-] のみ。最大64文字を上限とし、それ以外は拒否→生成。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQ_ID_HEADER = "X-Request-Id";
    public static final String REQ_ID_PARAM  = "requestId";
    public static final String MDC_KEY       = "req.id";

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._\\-]+");
    private static final int MAX_LEN = 64;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        // 1) 受信値の取り出し（ヘッダ優先→クエリparam）
        String rid = firstNonEmpty(request.getHeader(REQ_ID_HEADER),
                                   request.getParameter(REQ_ID_PARAM));

        // 2) サニタイズ（不正/空は再生成）
        rid = sanitize(rid);
        if (rid == null) {
            rid = generate();
        }

        // 3) レスポンスヘッダに常に返す（例外時でも相関できる）
        response.setHeader(REQ_ID_HEADER, rid);

        // 4) ログ相関IDをMDCに積む（必ずfinallyで剥がす）
        MDC.put(MDC_KEY, rid);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** UUIDベース（32hex） */
    private static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 許容文字/長さチェック、不正なら null */
    private static String sanitize(String in) {
        if (in == null) return null;
        String s = in.trim();
        if (s.isEmpty()) return null;
        if (s.length() > MAX_LEN) s = s.substring(0, MAX_LEN);
        return SAFE.matcher(s).matches() ? s : null;
    }

    private static String firstNonEmpty(String... xs) {
        if (xs == null) return null;
        for (String x : xs) {
            if (x != null && !x.isBlank()) return x;
        }
        return null;
    }
}
