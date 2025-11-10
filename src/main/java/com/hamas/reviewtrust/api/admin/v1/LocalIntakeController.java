package com.hamas.reviewtrust.api.admin.v1;

import com.hamas.reviewtrust.domain.products.service.LocalProductIntakeService;
import com.hamas.reviewtrust.domain.products.service.LocalProductIntakeService.CsvIngestResult;
import com.hamas.reviewtrust.domain.products.service.LocalProductIntakeService.SnapshotList;
import com.hamas.reviewtrust.domain.products.service.LocalProductIntakeService.UploadResult;
import com.hamas.reviewtrust.domain.products.service.LocalProductIntakeService.SnapshotSummary;
import com.hamas.reviewtrust.domain.products.service.ProductIntakeService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
@Profile("!dev")
@RequestMapping("/api/admin/local-intake")
@PreAuthorize("hasRole('ADMIN')")
public class LocalIntakeController {

    private final LocalProductIntakeService localIntakeService;

    public LocalIntakeController(LocalProductIntakeService localIntakeService) {
        this.localIntakeService = localIntakeService;
    }

    @PostMapping("/url")
    public ResponseEntity<Map<String, Object>> ingestUrl(@RequestBody Map<String, String> payload) {
        String url = payload != null ? payload.get("url") : null;
        ProductIntakeService.Result result = localIntakeService.ingestUrl(url);
        return ResponseEntity.ok(Map.of(
                "productId", result.product().getId(),
                "asin", result.snapshot().getAsin(),
                "created", result.created(),
                "partial", result.snapshot().isPartial()
        ));
    }

    @PostMapping("/csv")
    public CsvIngestResult ingestCsv(@RequestBody(required = false) Map<String, String> payload) {
        Path override = null;
        if (payload != null) {
            String pathValue = payload.get("path");
            if (StringUtils.hasText(pathValue)) {
                override = Path.of(pathValue.trim());
            }
        }
        return localIntakeService.ingestCsv(override);
    }

    @GetMapping("/snapshots")
    public SnapshotList listSnapshots(@RequestParam(name = "includeUploaded", defaultValue = "false") boolean includeUploaded,
                                      @RequestParam(name = "limit", defaultValue = "100") int limit) {
        return localIntakeService.listSnapshots(includeUploaded, Math.max(1, Math.min(limit, 500)));
    }

    @PostMapping("/upload")
    public UploadResult uploadSnapshots(@RequestBody UploadRequest request) {
        List<UUID> ids = request.snapshotIds();
        Objects.requireNonNull(ids, "snapshotIds are required");
        return localIntakeService.uploadSnapshots(ids, request.targetBaseUrl(), request.authToken());
    }

    public record UploadRequest(List<UUID> snapshotIds, String targetBaseUrl, String authToken) { }
}
