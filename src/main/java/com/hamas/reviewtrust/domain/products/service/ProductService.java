package com.hamas.reviewtrust.domain.products.service;

import com.hamas.reviewtrust.domain.products.dto.ProductListItem;
import com.hamas.reviewtrust.domain.products.entity.Product;
import com.hamas.reviewtrust.domain.products.repo.ProductRepository;
import com.hamas.reviewtrust.domain.reviews.service.ScoreService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 商品登録／検索／可視制御を行うサービス。
 * - register(input): URL or ASIN を受けて商品を登録（冪等）
 * - list/search: q/visible を受けて最大100件返却
 * - toggleVisibility: 管理用の可視制御
 * 想定: /api/products の入力は URL または ASIN のみで、title/name は後続処理で正式化する。
 */
@Service
public class ProductService {

    private final ProductRepository repo;
    private final ScoreService scoreService;

    public ProductService(ProductRepository repo, ScoreService scoreService) {
        this.repo = repo;
        this.scoreService = scoreService;
    }

    /** URL or ASIN を受けて ASIN を抽出し登録（存在すれば既存を返す）。 */
    @Transactional
    public RegistrationResult register(String input) {
        String asin = extractAsin(input);
        if (asin == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "invalid product input");
        }

        String url = canonicalUrl(input, asin);
        String fallbackLabel = "Auto-" + asin;

        return repo.findByAsin(asin)
                .map(existing -> {
                    boolean updated = false;
                    if (existing.getName() == null || existing.getName().isBlank()) {
                        existing.setName(fallbackLabel);
                        updated = true;
                    }
                    if (existing.getTitle() == null || existing.getTitle().isBlank()) {
                        existing.setTitle(fallbackLabel);
                        updated = true;
                    }
                    if (existing.getUrl() == null || existing.getUrl().isBlank()) {
                        existing.setUrl(url);
                        updated = true;
                    }
                    Product saved = updated ? repo.save(existing) : existing;
                    return new RegistrationResult(saved, false, updated);
                })
                .orElseGet(() -> {
                    Product p = new Product(null, asin, fallbackLabel, fallbackLabel, url, true, null, null);
                    Product saved = repo.save(p);
                    return new RegistrationResult(saved, true, true);
                });
    }

    /** 検索（最大100件）。q=null なら全件、visible=null で絞り込みなし。 */
    @Transactional(readOnly = true)
    public List<Product> list(String q, Boolean visible, int limit) {
        List<Product> base = repo.search(nz(q), visible);
        return base.stream().limit(Math.max(1, Math.min(100, limit))).toList();
    }

    @Transactional(readOnly = true)
    public List<Product> findProducts(Boolean visible) {
        return repo.search(null, visible);
    }

    @Transactional(readOnly = true)
    public List<ProductListItem> findProductSummaries(String query,
                                                      String tag,
                                                      Boolean visible,
                                                      int page,
                                                      int pageSize) {
        int safePage = Math.max(0, page);
        int safeSize = clampPageSize(pageSize);
        String normalizedQuery = nz(query);
        String titleQuery = normalizedQuery != null ? "%" + normalizedQuery.toLowerCase(Locale.ROOT) + "%" : null;
        String asinQuery = normalizedQuery != null ? "%" + normalizedQuery.toUpperCase(Locale.ROOT) + "%" : null;
        String normalizedTag = normalizeTag(tag);

        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<Product> pageResult = repo.searchWithTag(visible, titleQuery, asinQuery, normalizedTag, pageable);

        return pageResult.getContent()
                .stream()
                .map(this::toListItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public Product get(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found"));
    }

    /** 可視性の切り替え（管理API向け）。 */
    @Transactional
    public Product toggleVisibility(UUID id, boolean visible) {
        Product p = get(id);
        p.setVisible(visible);
        return repo.save(p);
    }

    @Transactional(readOnly = true)
    public Optional<Product> findByAsin(String asin) {
        if (!StringUtils.hasText(asin)) {
            return Optional.empty();
        }
        return repo.findByAsin(asin.trim());
    }

    // --- helpers ---

    private static final Pattern ASIN_IN_URL = Pattern.compile(
            "/(?:dp|gp/product|o/ASIN|product-reviews)/([A-Za-z0-9]{10})(?:[/?#]|$)");
    private static final Pattern ASIN_RAW = Pattern.compile("^[A-Za-z0-9]{10}$");

    /** 入力文字列から ASIN を抽出（URL/ASIN いずれでも可）。 */
    private String extractAsin(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty()) return null;

        if (ASIN_RAW.matcher(s).matches()) {
            return s.toUpperCase();
        }

        try {
            URI uri = URI.create(s);
            String path = Optional.ofNullable(uri.getPath()).orElse("");
            Matcher m = ASIN_IN_URL.matcher(path);
            if (m.find()) return m.group(1).toUpperCase();
        } catch (Exception ignore) { /* not a URL */ }

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
        return "https://www.amazon.co.jp/dp/" + asin;
    }

    private String nz(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeTag(String value) {
        String trimmed = nz(value);
        return trimmed != null ? trimmed.toLowerCase(Locale.ROOT) : null;
    }

    private int clampPageSize(int requested) {
        if (requested < 1) {
            return 1;
        }
        return Math.min(requested, 200);
    }

    private ProductListItem toListItem(Product product) {
        var productScore = scoreService.getCachedScore(product.getId());
        double scoreValue = productScore != null ? productScore.score() : 0.0;
        String rank = productScore != null && productScore.rank() != null ? productScore.rank() : "A";
        return ProductListItem.from(product, scoreValue, rank);
    }

    public record RegistrationResult(Product product, boolean created, boolean updated) { }
}
