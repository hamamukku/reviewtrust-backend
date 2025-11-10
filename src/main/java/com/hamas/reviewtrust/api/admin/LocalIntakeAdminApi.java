package com.hamas.reviewtrust.api.admin;

import com.hamas.reviewtrust.domain.reviews.service.DevReviewIngestor;
import com.hamas.reviewtrust.domain.reviews.service.ReviewSnapshotScanner;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Profile("dev")
@RestController
@RequestMapping("/api/admin/local-intake")
public class LocalIntakeAdminApi {

    private final ReviewSnapshotScanner scanner;
    private final DevReviewIngestor ingestor;

    public LocalIntakeAdminApi(ReviewSnapshotScanner scanner, DevReviewIngestor ingestor) {
        this.scanner = scanner;
        this.ingestor = ingestor;
    }

    @GetMapping("/snapshots")
    public ResponseEntity<List<ReviewSnapshotScanner.SnapshotSummary>> list(
            @RequestParam(name = "includeUploaded", defaultValue = "true") boolean includeUploaded) {
        return ResponseEntity.ok(scanner.listSummaries(includeUploaded));
    }

    @GetMapping("/reviews")
   public ResponseEntity<ReviewSnapshotScanner.AsinReviews> reviews(
            @RequestParam("asin") @NotBlank String asin) {
        return ResponseEntity.ok(scanner.getReviewsOf(asin));
    }

    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingest(@RequestBody IngestRequest request) {
        if (request == null || !StringUtils.hasText(request.asin())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "asin is required");
        }
        if (!StringUtils.hasText(request.dataset())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dataset is required");
        }
        DevReviewIngestor.IngestResult result = ingestor.ingest(request.asin(), request.dataset());
        return ResponseEntity.ok(IngestResponse.from(result));
    }

    public record IngestRequest(String asin, String dataset) { }

    public record IngestResponse(
            String asin,
            int inserted,
            int updated,
            int positives,
            int negatives,
            boolean histogramSaved,
            Instant capturedAt
    ) {
        static IngestResponse from(DevReviewIngestor.IngestResult result) {
            return new IngestResponse(
                    result.asin(),
                    result.inserted(),
                    result.updated(),
                    result.positives(),
                    result.negatives(),
                    result.histogramSaved(),
                    result.capturedAt()
            );
        }
    }
}
