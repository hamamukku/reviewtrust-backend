package com.hamas.reviewtrust.domain.scraping.repo;

import com.hamas.reviewtrust.domain.scraping.entity.ScrapeJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ScrapeJobRepository extends JpaRepository<ScrapeJob, UUID> {

    /**
     * Finds all scrape jobs by the given state.
     *
     * <p>This derives the query from the method name. It returns a list
     * of jobs with the desired state. The scheduler uses this to pick up
     * queued jobs for processing. When no jobs match the state an empty list
     * is returned.</p>
     *
     * @param state the state to filter by
     * @return jobs in the given state
     */
    java.util.List<ScrapeJob> findByState(ScrapeJob.State state);
}

