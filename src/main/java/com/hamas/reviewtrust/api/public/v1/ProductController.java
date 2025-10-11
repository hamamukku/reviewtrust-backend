package com.hamas.reviewtrust.api.public.v1;

import com.hamas.reviewtrust.api.publicapi.v1.dto.ProductDtos; // 応答DTOは既存の publicapi 側を利用
import com.hamas.reviewtrust.api.publicapi.v1.dto.ProductDtos.ProductDetail;
import com.hamas.reviewtrust.api.publicapi.v1.dto.ProductDtos.ProductSummary;
import com.hamas.reviewtrust.domain.products.entity.Product;
import com.hamas.reviewtrust.domain.products.mapper.ProductMapper;
import com.hamas.reviewtrust.domain.products.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * 公開API（/api/products）
 * - POST /api/products        : URL/ASINを登録（idempotent）
 * - GET  /api/products        : 一覧（q/visible/limit）
 * - GET  /api/products/{id}   : 詳細（visible=false は 410 Gone）
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    /** 商品登録（URL or ASIN）。201 Created + Location を返す。 */
    @PostMapping
    public ResponseEntity<ProductSummary> create(@Valid @RequestBody CreateProductRequest req) {
        Product p = service.register(req.input());
        ProductSummary body = ProductMapper.toSummary(p);
        return ResponseEntity
                .created(URI.create("/api/products/" + p.getId()))
                .body(body);
    }

    /** 一覧（最大100件）。既定では visible=true。 */
    @GetMapping
    public List<ProductSummary> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "true") boolean visible,
            @RequestParam(defaultValue = "50") int limit
    ) {
        int clipped = Math.max(1, Math.min(100, limit));
        return service.list(q, visible, clipped).stream()
                .map(ProductMapper::toSummary)
                .toList();
    }

    /** 詳細。非表示(visible=false)の場合は 410 Gone（MVP仕様）。 */
    @GetMapping("/{id}")
    public ProductDetail get(@PathVariable UUID id) {
        Product p = service.get(id); // 404はサービス層で投げる
        if (!p.isVisible()) {
            throw new ResponseStatusException(HttpStatus.GONE, "product is hidden");
        }
        return ProductMapper.toDetail(p);
    }

    /** 旧 ProductDtos.CreateProductRequest を置き換える最小 DTO（このクラス内限定） */
    public static final class CreateProductRequest {
        @NotBlank
        private String input;
        public CreateProductRequest() {}
        public CreateProductRequest(String input) { this.input = input; }
        public String input() { return input; }
        public void setInput(String input) { this.input = input; }
    }
}
