package com.hamas.reviewtrust.domain.scraping.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scrape_jobs")
public class ScrapeJob {
    public enum State { queued, running, ok, failed }

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "product_id", columnDefinition = "uuid", nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state;

    @Column(name = "target_count")
    private Integer targetCount;

    @Column(name = "fetched_count")
    private Integer fetchedCount;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "filters_json", columnDefinition = "text")
    private String filtersJson;

    protected ScrapeJob() {}

    public static ScrapeJob queued(UUID productId, String filtersJson) {
        ScrapeJob j = new ScrapeJob();
        j.id = UUID.randomUUID();
        j.productId = productId;
        j.state = State.queued;
        j.fetchedCount = 0;
        j.filtersJson = filtersJson;
        return j;
    }

    // ==== 状態遷移 ====
    public void markRunning() {
        this.state = State.running;
        this.startedAt = Instant.now();
        this.finishedAt = null;
    }
    public void markOk(int fetchedCount, int targetCount) {
        this.fetchedCount = fetchedCount;
        this.targetCount = targetCount;
        this.state = State.ok;
        this.finishedAt = Instant.now();
    }
    public void markFailed() {
        this.state = State.failed;
        this.finishedAt = Instant.now();
    }

    // ==== getter / setter ====
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }
    public State getState() { return state; }
    public void setState(State state) { this.state = state; }
    public Integer getTargetCount() { return targetCount; }
    public void setTargetCount(Integer targetCount) { this.targetCount = targetCount; }
    public Integer getFetchedCount() { return fetchedCount; }
    public void setFetchedCount(Integer fetchedCount) { this.fetchedCount = fetchedCount; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public String getFiltersJson() { return filtersJson; }
    public void setFiltersJson(String filtersJson) { this.filtersJson = filtersJson; }
}
