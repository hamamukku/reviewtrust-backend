package com.hamas.reviewtrust.api.publicapi.v1.dto;import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProductDtos {
    private ProductDtos() {}

    // ===== Summary =====
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({
            "id", "name", "url",
            "rank", "score",
            "lastFetchedAt"
    })
    public static record ProductSummary(
            String id,
            String name,
            String url,
            String rank,
            Integer score,
            Instant lastFetchedAt
    ) implements Serializable {
        // 既存コード互換: new ProductDtos.ProductSummary(UUID, String, String)
        public ProductSummary(UUID id, String name, String url) {
            this(id != null ? id.toString() : null, name, url, null, null, null);
        }
    }

    // ===== Detail =====
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({
            "id", "name", "url", "images",
            "reviewCountAmazon", "reviewCountUser",
            "scoreAmazon", "scoreUser",
            "rankAmazon", "rankUser",
            "flags",
            "lastFetchedAt"
    })
    public static record ProductDetail(
            String id,
            String name,
            String url,
            List<String> images,

            Integer reviewCountAmazon,
            Integer reviewCountUser,

            Integer scoreAmazon,
            Integer scoreUser,
            String  rankAmazon,
            String  rankUser,

            Map<String, Object> flags,

            Instant lastFetchedAt
    ) implements Serializable {
        // 既存コード互換:
        // new ProductDtos.ProductDetail(UUID, String, String, String, boolean, Instant, Instant)
        //   id, name, url, imageUrl, suspicious, lastFetchedAtAmazon, lastFetchedAtUser
        public ProductDetail(UUID id,
                             String name,
                             String url,
                             String imageUrl,
                             boolean suspicious,
                             Instant lastFetchedAtAmazon,
                             Instant lastFetchedAtUser) {
            this(
                id != null ? id.toString() : null,
                name,
                url,
                imageUrl != null ? List.of(imageUrl) : List.of(),
                null, // reviewCountAmazon
                null, // reviewCountUser
                null, // scoreAmazon
                null, // scoreUser
                null, // rankAmazon
                null, // rankUser
                Map.of("suspicious", suspicious),
                (lastFetchedAtAmazon != null) ? lastFetchedAtAmazon : lastFetchedAtUser
            );
        }
    }
}


