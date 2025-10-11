package com.hamas.reviewtrust.domain.scraping.repo;

import com.hamas.reviewtrust.domain.scraping.entity.ScrapeJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ScrapeJobRepository extends JpaRepository<ScrapeJob, UUID> {}
