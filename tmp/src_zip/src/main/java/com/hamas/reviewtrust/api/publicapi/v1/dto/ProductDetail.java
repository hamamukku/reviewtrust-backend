package com.hamas.reviewtrust.api.publicapi.v1.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "id", "name", "url", "images",
        "reviewCountAmazon", "reviewCountUser",
        "scoreAmazon", "scoreUser",
        "rankAmazon", "rankUser",
        "flags",
        "lastFetchedAt"
})
public record ProductDetail(
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
    // new ProductDetail(UUID, String, String, String, boolean, Instant, Instant)
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

