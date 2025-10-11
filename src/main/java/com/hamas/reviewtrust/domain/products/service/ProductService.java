// ProductService.java (placeholder)
package com.hamas.reviewtrust.domain.products.service;

import com.hamas.reviewtrust.domain.products.entity.Product;
import com.hamas.reviewtrust.domain.products.repo.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 商品登録/検索/可視性切替の最小サービス。
 * - register(input): URL or ASIN を受け取り正規化・重複除去（idempotent）
 * - list/search: q/visible を受けて最大100件返す
 * - toggleVisibility: 管理用の表示/非表示切替
 * 仕様: /api/products の要件（URL or ASIN登録・検索・visible反映）に準拠。 
 */
@Service
public class ProductService {

    private final ProductRepository repo;

    public ProductService(ProductRepository repo) {
        this.repo = repo;
    }

    /** URL or ASIN を受け取り、ASINを抽出して登録（既存なら既存を返す）。 */
    @Transactional
    public Product register(String input) {
        String asin = extractAsin(input);
        if (asin == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "invalid product input");
        }
        return repo.findByAsin(asin).orElseGet(() -> {
            String url = canonicalUrl(input, asin);
            Product p = new Product(null, asin, asin, url, true, null, null);
            return repo.save(p);
        });
    }

    /** 検索（最大100件）。q=null なら全件、visible=null で可視性条件なし。 */
    @Transactional(readOnly = true)
    public List<Product> list(String q, Boolean visible, int limit) {
        List<Product> base = repo.search(nz(q), visible);
        return base.stream().limit(Math.max(1, Math.min(100, limit))).toList();
    }

    @Transactional(readOnly = true)
    public Product get(UUID id) {
        return repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found"));
    }

    /** 表示/非表示切替（管理API用）。 */
    @Transactional
    public Product toggleVisibility(UUID id, boolean visible) {
        Product p = get(id);
        p.setVisible(visible);
        return repo.save(p);
    }

    // --- helpers ---

    private static final Pattern ASIN_IN_URL = Pattern.compile(
            "/(?:dp|gp/product|o/ASIN|product-reviews)/([A-Za-z0-9]{10})(?:[/?#]|$)");
    private static final Pattern ASIN_RAW = Pattern.compile("^[A-Za-z0-9]{10}$");

    /** 入力から ASIN を抽出（URL/ASIN生値どちらでもOK）。 */
    private String extractAsin(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty()) return null;

        // 生のASIN
        if (ASIN_RAW.matcher(s).matches()) {
            return s.toUpperCase();
        }

        // URL から抽出
        try {
            URI uri = URI.create(s);
            String path = Optional.ofNullable(uri.getPath()).orElse("");
            Matcher m = ASIN_IN_URL.matcher(path);
            if (m.find()) return m.group(1).toUpperCase();
        } catch (Exception ignore) { /* not a URL */ }

        // 雑に含まれていないかも一応チェック
        Matcher any = Pattern.compile("([A-Za-z0-9]{10})").matcher(s);
        if (any.find()) return any.group(1).toUpperCase();

        return null;
    }

    private String canonicalUrl(String input, String asin) {
        try {
            URI u = URI.create(input);
            if (u.getHost() != null && u.getHost().contains("amazon")) {
                String host = u.getHost();
                return "https://" + host + "/dp/" + asin;
            }
        } catch (Exception ignore) { /* not a URL */ }
        // ASINのみの登録なら汎用の日本向けURLに正規化
        return "https://www.amazon.co.jp/dp/" + asin;
    }

    private String nz(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
