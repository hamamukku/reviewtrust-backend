package com.hamas.reviewtrust;

import com.hamas.reviewtrust.api.publicapi.v1.ScoresController;
import com.hamas.reviewtrust.api.publicapi.v1.dto.ProductScoreResponse;
import com.hamas.reviewtrust.domain.products.entity.Product;
import com.hamas.reviewtrust.domain.products.service.ProductScoreService;
import com.hamas.reviewtrust.domain.products.service.ProductService;
import com.hamas.reviewtrust.domain.scoring.catalog.ScoreModels;
import com.hamas.reviewtrust.domain.scoring.engine.Ranker;
import com.hamas.reviewtrust.domain.scoring.profile.ThresholdProvider;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public final class ManualTestRunner {

    public static void main(String[] args) throws Exception {
        try {
            testRanker();
            testScoresControllerContract();
            testHealthEndpoint();
            System.out.println("MANUAL TESTS PASSED");
        } catch (AssertionError | Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void testRanker() {
        ThresholdProvider.Thresholds thresholds = ThresholdProvider.Thresholds.defaults();
        require(Ranker.assign(34).name().equals(ScoreModels.Rank.A.name()), "Score 34 should be rank A");
        require(Ranker.assign(35).name().equals(ScoreModels.Rank.B.name()), "Score 35 should be rank B");
        require(Ranker.assign(80).name().equals(ScoreModels.Rank.C.name()), "Score 80 should be rank C");

        ScoreModels.FeatureSnapshot suspicious = new ScoreModels.FeatureSnapshot(0.9, 0.6, 0.0, 0.0);
        require(
                Ranker.judgeSakura(suspicious, thresholds) == ScoreModels.SakuraJudge.SAKURA,
                "High bias + dup => SAKURA"
        );
    }

    private static void testScoresControllerContract() {
        ProductService productService = Mockito.mock(ProductService.class);
        ProductScoreService scoreMock = Mockito.mock(ProductScoreService.class);
        com.hamas.reviewtrust.domain.reviews.service.ScoreService legacyMock =
                Mockito.mock(com.hamas.reviewtrust.domain.reviews.service.ScoreService.class);

        Product product = new Product(
                java.util.UUID.randomUUID(),
                "B00TEST123",
                "Demo Product",
                "Demo Product",
                "https://example.com",
                true,
                Instant.now(),
                Instant.now()
        );

        ProductScoreResponse payload = new ProductScoreResponse();
        payload.setProductId(java.util.UUID.randomUUID());
        payload.setAsin("B00TEST123");
        payload.setProductName("Demo Product");
        payload.setAverageScore(4.5);
        payload.setReviewCount(123);
        payload.setHistogram(Map.of(5, 100L));
        ProductScoreResponse.ScoreBlock amazonBlock = new ProductScoreResponse.ScoreBlock(91.0, "A");
        amazonBlock.setDisplayScore(91.0);
        payload.setAmazon(amazonBlock);

        Mockito.when(productService.findByAsin("B00TEST123")).thenReturn(Optional.of(product));
        Mockito.when(scoreMock.findLatestByAsin("B00TEST123")).thenReturn(Optional.of(payload));
        Mockito.when(legacyMock.recomputeForProduct(payload.getProductId()))
                .thenReturn(new com.hamas.reviewtrust.domain.reviews.service.ScoreService.ProductScore(
                        payload.getProductId(),
                        42.0,
                        58.0,
                        "B",
                        "UNLIKELY",
                        java.util.List.of("ATTN_DUPLICATE"),
                        java.util.List.of(),
                        Map.of("total_reviews", 12)
                ));

        ScoresController controller = new ScoresController(productService, scoreMock, legacyMock);
        ResponseEntity<ProductScoreResponse> response = controller.getScores("B00TEST123");
        require(response.getStatusCode().is2xxSuccessful(), "Scores endpoint should return 200");
        ProductScoreResponse body = response.getBody();
        require(body != null, "Payload must not be null");
        require("Demo Product".equals(body.getProductName()), "productName must match");
        require(body.getHistogram().getOrDefault(5, 0L) == 100L, "histogram should include star counts");
        require("UNLIKELY".equals(body.getSakuraJudge()), "sakuraJudge should propagate to response");
    }

    private static void testHealthEndpoint() {
        System.setProperty("spring.profiles.active", "test");
        ConfigurableApplicationContext ctx = SpringApplication.run(ReviewTrustApplication.class);
        try {
            HealthEndpoint endpoint = ctx.getBean(HealthEndpoint.class);
            HealthComponent component = endpoint.health();
            require(component.getStatus() != null, "Health status should not be null");
            require("UP".equalsIgnoreCase(component.getStatus().getCode()), "Health status must be UP");
        } finally {
            SpringApplication.exit(ctx);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
