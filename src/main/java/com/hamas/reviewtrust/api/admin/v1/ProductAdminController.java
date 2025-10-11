// ProductAdminController.java (placeholder)
package com.hamas.reviewtrust.api.admin.v1;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hamas.reviewtrust.domain.products.entity.Product;
import com.hamas.reviewtrust.domain.products.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 管理：商品可視性の切替（表示/非表示）。
 * 仕様: POST /api/admin/products/{id}/toggle-visibility
 * body: {"visible":true|false}
 */
@RestController
@RequestMapping("/api/admin/products")
public class ProductAdminController {

    private final ProductService products;

    public ProductAdminController(ProductService products) {
        this.products = products;
    }

    @PostMapping("/{id}/toggle-visibility")
    public ResponseEntity<ToggleVisibilityResult> toggle(
            @PathVariable UUID id,
            @RequestBody ToggleVisibilityRequest req
    ) {
        Product p = products.toggleVisibility(id, req.visible());
        return ResponseEntity.ok(new ToggleVisibilityResult(p.getId(), p.isVisible()));
    }

    // 入出力DTO
    public static final class ToggleVisibilityRequest {
        private final boolean visible;

        @JsonCreator
        public ToggleVisibilityRequest(@JsonProperty("visible") boolean visible) {
            this.visible = visible;
        }
        public boolean visible() { return visible; }
    }

    public record ToggleVisibilityResult(UUID productId, boolean visible) { }
}
