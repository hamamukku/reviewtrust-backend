package com.hamas.reviewtrust.api.admin.v1;

import com.hamas.reviewtrust.domain.products.entity.Product;
import com.hamas.reviewtrust.domain.products.service.ProductIntakeService;
import com.hamas.reviewtrust.domain.products.service.ProductIntakeService.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/products")
public class ProductIntakeController {

    private final ProductIntakeService intakeService;

    public ProductIntakeController(ProductIntakeService intakeService) {
        this.intakeService = intakeService;
    }

    @PostMapping("/intake/html")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> intakeHtml(@RequestBody Map<String, String> payload) {
        if (payload == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }

        String html = payload.get("html");
        if (!StringUtils.hasText(html)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "html is required");
        }

        String sourceUrl = payload.get("sourceUrl");
        Result result = intakeService.registerOrUpdateFromHtml(html, sourceUrl);
        Product product = result.product();

        return ResponseEntity.ok(Map.of(
                "productId", product.getId(),
                "asin", product.getAsin(),
                "created", result.created(),
                "partial", result.snapshot().isPartial(),
                "status", "OK"
        ));
    }
}

