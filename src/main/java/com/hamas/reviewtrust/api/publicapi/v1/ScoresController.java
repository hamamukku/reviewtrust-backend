package com.hamas.reviewtrust.api.publicapi.v1;

import com.hamas.reviewtrust.api.publicapi.v1.dto.ProductScoreResponse;
import com.hamas.reviewtrust.domain.products.entity.Product;
import com.hamas.reviewtrust.domain.products.service.ProductScoreService;
import com.hamas.reviewtrust.domain.products.service.ProductService;
import com.hamas.reviewtrust.domain.reviews.service.ScoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Public endpoint exposing the latest scraped snapshot metrics.
 */
@RestController
@RequestMapping("/api/products")
public class ScoresController {

    private static final Logger log = LoggerFactory.getLogger(ScoresController.class);

    private final ProductService productService;
    private final ProductScoreService productScoreService;
    private final ScoreService scoreService;

    public ScoresController(ProductService productService,
                            ProductScoreService productScoreService,
                            ScoreService scoreService) {
        this.productService = productService;
        this.productScoreService = productScoreService;
        this.scoreService = scoreService;
    }

    /**
     * NOTE: Response payload now includes {@code productName} so the snapshot UI can display titles without
     * an extra lookup. Frontend clients must consume the new JSON structure.
     */
    @GetMapping("/{idOrAsin}/scores")
    public ResponseEntity<ProductScoreResponse> getScores(@PathVariable("idOrAsin") String idOrAsin) {
        if (!StringUtils.hasText(idOrAsin)) {
            return ResponseEntity.badRequest().build();
        }
        String token = idOrAsin.trim();
        log.debug("RESOLVE: received idOrAsin='{}'", token);
        try {
            UUID uuid = UUID.fromString(token);
            Product product = productService.get(uuid);
            log.debug("RESOLVE: treated '{}' as UUID -> productId={}", token, product.getId());
            return respondWithProduct(product);
        } catch (IllegalArgumentException ignored) {
            return respondWithAsin(token);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error while getting scores for '{}'", token, ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to get scores", ex);
        }
    }

    private ResponseEntity<ProductScoreResponse> respondWithAsin(String asin) {
        Product product = productService.findByAsin(asin)
                .orElseThrow(() -> {
                    log.debug("getScores: product not found for ASIN={}", asin);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
                });
        log.debug("RESOLVE: treated '{}' as ASIN -> productId={}", asin, product.getId());
        return respondWithProduct(product);
    }

    private ResponseEntity<ProductScoreResponse> respondWithProduct(Product product) {
        String asin = product.getAsin();
        if (!StringUtils.hasText(asin)) {
            log.warn("Product {} has no ASIN; cannot resolve scores", product.getId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product ASIN missing");
        }
        return productScoreService.findLatestByAsin(asin.trim())
                .map(this::attachOverallScore)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    log.info("No product snapshot found for asin={} productId={}", asin, product.getId());
                    return ResponseEntity.notFound().build();
                });
    }

    private ProductScoreResponse attachOverallScore(ProductScoreResponse response) {
        if (response.getProductId() != null) {
            ScoreService.ProductScore computed = scoreService.recomputeForProduct(response.getProductId());
            if (computed != null) {
                response.setOverall(toScoreBlock(computed));
            }
        }
        if (response.getOverall() == null && response.getAmazon() != null) {
            log.debug("attachOverallScore: overall missing; falling back to amazon score for productId={}",
                    response.getProductId());
            response.setOverall(response.getAmazon());
        }
        return response;
    }

    private ProductScoreResponse.ScoreBlock toScoreBlock(ScoreService.ProductScore computed) {
        ProductScoreResponse.ScoreBlock block = new ProductScoreResponse.ScoreBlock();
        block.setScore(computed.score());
        block.setDisplayScore(computed.displayScore());
        block.setRank(computed.rank());
        block.setSakuraJudge(computed.sakuraJudge());
        block.setFlags(computed.flags());
        block.setRules(toRuleEntries(computed.rules()));
        block.setMetrics(computed.metrics());
        return block;
    }

    private List<ProductScoreResponse.RuleEntry> toRuleEntries(List<ScoreService.RuleEvidence> rules) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        return rules.stream()
                .map(rule -> new ProductScoreResponse.RuleEntry(
                        rule.id(),
                        rule.value(),
                        rule.warn(),
                        rule.crit(),
                        rule.weight(),
                        rule.points(),
                        rule.extra()
                ))
                .toList();
    }
}
