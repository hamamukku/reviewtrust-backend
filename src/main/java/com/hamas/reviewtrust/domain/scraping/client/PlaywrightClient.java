// PlaywrightClient.java (placeholder)
package com.hamas.reviewtrust.domain.scraping.client;

import com.hamas.reviewtrust.domain.scraping.exception.ScrapingExceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Playwright 本実装の置き換え前提の「HTTPフォールバック版」。
 * - Header偽装, タイムアウト, 簡易リトライ
 * - 429/403 は「ブロック系」として扱う
 * - scrollAndGet は暫定で静的取得にフォールバック
 *
 * 将来: com.microsoft.playwright を導入し、実ブラウザで DOM 安定化 → innerHTML 取得へ置換。
 */
@Component
public class PlaywrightClient implements BrowserClient {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightClient.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    @Override
    public String getHtml(String url) throws Exception {
        return getHtml(url, 0);
    }

    @Override
    public String getHtml(String url, int waitMillis) throws Exception {
        if (waitMillis > 0) {
            Thread.sleep(Math.min(waitMillis, 5_000));
        }
        // ポライトネス: 0.4–1.2s ランダムスリープ
        Thread.sleep(ThreadLocalRandom.current().nextLong(400, 1_200));

        int attempts = 2; // 1回リトライ
        Exception last = null;

        for (int i = 0; i < attempts; i++) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("User-Agent",
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                                        + "AppleWebKit/537.36 (KHTML, like Gecko) "
                                        + "Chrome/124.0 Safari/537.36")
                        .header("Accept", "text/html,application/xhtml+xml")
                        .header("Accept-Language", "ja,en;q=0.9")
                        .GET()
                        .build();

                HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
                int sc = res.statusCode();

                if (sc == 429 || sc == 403) {
                    throw ScrapingExceptions.blocked("blocked or rate-limited: HTTP " + sc);
                }
                if (sc >= 400) {
                    throw ScrapingExceptions.network("http error: " + sc, null);
                }
                // 文字コード判定は簡易にUTF-8固定（必要に応じて検出へ拡張）
                return new String(res.body(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                last = e;
                // 指数バックオフ
                Thread.sleep(600L * (1L << i));
            }
        }
        if (last instanceof ScrapingExceptions.ScrapeException se) throw se;
        throw ScrapingExceptions.failed("failed to fetch via HTTP fallback", last);
    }

    @Override
    public String scrollAndGet(String url, int maxScrolls, int delayMs) throws Exception {
        log.warn("scrollAndGet fallback: static fetch used (url={})", url);
        return getHtml(url, delayMs);
    }
}
