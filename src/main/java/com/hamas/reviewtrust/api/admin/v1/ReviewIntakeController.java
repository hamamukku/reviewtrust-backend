package com.hamas.reviewtrust.api.admin.v1;

import com.hamas.reviewtrust.domain.reviews.service.ReviewIntakeService;
import com.hamas.reviewtrust.domain.reviews.service.ReviewIntakeService.FileMetadata;
import com.hamas.reviewtrust.domain.reviews.service.ReviewIntakeService.IntakeResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/review-intake")
@PreAuthorize("hasRole('ADMIN')")
public class ReviewIntakeController {

    private final ReviewIntakeService reviewIntakeService;

    public ReviewIntakeController(ReviewIntakeService reviewIntakeService) {
        this.reviewIntakeService = reviewIntakeService;
    }

    @GetMapping("/files")
    public FilesResponse listFiles(@RequestParam(value = "asin", required = false) String asin) {
        List<FileMetadata> items = reviewIntakeService.describeCandidateFiles(asin);
        return new FilesResponse(items.size(), items);
    }

    @PostMapping("/run")
    public IntakeResult run(@RequestBody(required = false) RunRequest request) {
        String asin = request != null ? request.asin() : null;
        return reviewIntakeService.ingestAll(asin);
    }

    public record RunRequest(String asin) { }

    public record FilesResponse(int count, List<FileMetadata> files) { }
}

