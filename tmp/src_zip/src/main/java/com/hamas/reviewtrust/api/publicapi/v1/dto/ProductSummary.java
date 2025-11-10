package com.hamas.reviewtrust.api.publicapi.v1.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "id", "name", "url",
        "rank", "score",
        "lastFetchedAt"
})
public record ProductSummary(
        String id,
        String name,
        String url,
        String rank,
        Integer score,
        Instant lastFetchedAt
) implements Serializable {
    // 既存コード互換: new ProductSummary(UUID, String, String)
    public ProductSummary(UUID id, String name, String url) {
        this(id != null ? id.toString() : null, name, url, null, null, null);
    }
}

