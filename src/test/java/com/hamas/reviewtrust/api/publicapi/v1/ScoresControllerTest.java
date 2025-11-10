package com.hamas.reviewtrust.api.publicapi.v1;

import com.hamas.reviewtrust.api.publicapi.v1.dto.ProductScoreResponse;
import com.hamas.reviewtrust.domain.products.entity.Product;
import com.hamas.reviewtrust.domain.products.service.ProductScoreService;
import com.hamas.reviewtrust.domain.products.service.ProductService;
import com.hamas.reviewtrust.domain.reviews.service.ScoreService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScoresController.class)
@ActiveProfiles("test")
class ScoresControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @MockBean
    private ProductScoreService productScoreService;

    @MockBean(name = "productScoreService")
    private ScoreService scoreService;

    @Test
    void resolvesByUuidAndReturnsOverallScore() throws Exception {
        UUID productId = UUID.randomUUID();
        Product product = new Product(productId, "B00TEST01", "Demo", "Demo", "https://example.com", true,
                Instant.now(), Instant.now());

        ProductScoreResponse payload = new ProductScoreResponse();
        payload.setProductId(productId);
        payload.setAsin("B00TEST01");
        payload.setProductName("Demo");
        payload.setHistogram(Map.of(5, 10L));
        ProductScoreResponse.ScoreBlock amazonBlock = new ProductScoreResponse.ScoreBlock(100.0, "A");
        amazonBlock.setDisplayScore(100.0);
        payload.setAmazon(amazonBlock);

        Mockito.when(productService.get(productId)).thenReturn(product);
        Mockito.when(productScoreService.findLatestByAsin("B00TEST01")).thenReturn(Optional.of(payload));
        Mockito.when(scoreService.recomputeForProduct(productId))
                .thenReturn(new ScoreService.ProductScore(
                        productId,
                        87.0,
                        13.0,
                        "B",
                        "LIKELY",
                        List.of("ATTN_DUPLICATE"),
                        List.of(),
                        Map.of("total_reviews", 5)
                ));

        mockMvc.perform(get("/api/products/{id}/scores", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId.toString()))
                .andExpect(jsonPath("$.overall.score").value(87.0))
                .andExpect(jsonPath("$.overall.rank").value("B"))
                .andExpect(jsonPath("$.overall.sakura_judge").value("LIKELY"))
                .andExpect(jsonPath("$.sakura_judge").value("LIKELY"))
                .andExpect(jsonPath("$.flags[0]").value("ATTN_DUPLICATE"))
                .andExpect(jsonPath("$.amazon.score").value(100.0));
    }

    @Test
    void resolvesByAsinAndReturnsSnapshotWhenOverallMissing() throws Exception {
        UUID productId = UUID.randomUUID();
        Product product = new Product(productId, "B00ASIN99", "Name", "Name",
                "https://example.com", true, Instant.now(), Instant.now());

        ProductScoreResponse payload = new ProductScoreResponse();
        payload.setProductId(productId);
        payload.setAsin("B00ASIN99");
        ProductScoreResponse.ScoreBlock amazonBlock = new ProductScoreResponse.ScoreBlock(75.0, "B");
        amazonBlock.setDisplayScore(75.0);
        payload.setAmazon(amazonBlock);

        Mockito.when(productService.findByAsin("B00ASIN99")).thenReturn(Optional.of(product));
        Mockito.when(productScoreService.findLatestByAsin("B00ASIN99")).thenReturn(Optional.of(payload));
        Mockito.when(scoreService.recomputeForProduct(productId)).thenReturn(null);

        mockMvc.perform(get("/api/products/{id}/scores", "B00ASIN99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId.toString()))
                .andExpect(jsonPath("$.overall.score").value(75.0))
                .andExpect(jsonPath("$.overall.rank").value("B"));
    }

    @Test
    void returnsNotFoundWhenUnknownAsin() throws Exception {
        Mockito.when(productService.findByAsin("UNKNOWN")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/products/{id}/scores", "UNKNOWN"))
                .andExpect(status().isNotFound());
    }
}
