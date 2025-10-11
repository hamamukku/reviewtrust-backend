// ScrapeFilters.java (placeholder)
package com.hamas.reviewtrust.domain.scraping.filter;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 取得フィルタの最小型。
 * 例: {"stars":[1], "imageOnly":true, "verifiedOnly":true}
 *
 * DB には ScrapeJob.filters(jsonb) として保存（null可）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScrapeFilters {

    /** 1..5 の重複なしリスト（例：★1だけ取得） */
    private List<Integer> stars;

    /** 画像付きレビューのみを対象にするか */
    private Boolean imageOnly;

    /** 「Amazonで購入」バッジ付きのみか */
    private Boolean verifiedOnly;

    public ScrapeFilters() { }

    public ScrapeFilters(List<Integer> stars, Boolean imageOnly, Boolean verifiedOnly) {
        this.stars = stars;
        this.imageOnly = imageOnly;
        this.verifiedOnly = verifiedOnly;
    }

    public boolean isEmpty() {
        return (stars == null || stars.isEmpty())
                && imageOnly == null
                && verifiedOnly == null;
    }

    /** 入力バリデーション（422想定のハンドリングは GlobalExceptionHandler 側） */
    public void validate() {
        if (stars != null) {
            if (stars.size() > 5) throw new IllegalArgumentException("stars: too many values");
            Set<Integer> uniq = new HashSet<>();
            for (Integer s : stars) {
                if (s == null || s < 1 || s > 5) {
                    throw new IllegalArgumentException("stars: each must be 1..5");
                }
                if (!uniq.add(s)) {
                    throw new IllegalArgumentException("stars: duplicate values are not allowed");
                }
            }
        }
    }

    // getters / setters
    public List<Integer> getStars() { return stars; }
    public void setStars(List<Integer> stars) { this.stars = stars; }
    public Boolean getImageOnly() { return imageOnly; }
    public void setImageOnly(Boolean imageOnly) { this.imageOnly = imageOnly; }
    public Boolean getVerifiedOnly() { return verifiedOnly; }
    public void setVerifiedOnly(Boolean verifiedOnly) { this.verifiedOnly = verifiedOnly; }
}
