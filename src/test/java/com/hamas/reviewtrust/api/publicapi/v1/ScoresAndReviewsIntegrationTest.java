package com.hamas.reviewtrust.api.publicapi.v1;

import com.hamas.reviewtrust.domain.products.entity.Product;
import com.hamas.reviewtrust.domain.products.repo.ProductRepository;
import com.hamas.reviewtrust.domain.reviews.entity.Review;
import com.hamas.reviewtrust.domain.reviews.repo.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class ScoresAndReviewsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void cleanDatabase() {
        reviewRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void scoreEndpointReflectsPersistedReviews() throws Exception {
        UUID productId = UUID.randomUUID();
        Product product = new Product(
                productId,
                "B0TESTREV1",
                "Sample Product",
                "Sample Product",
                "https://example.com",
                true,
                Instant.now(),
                Instant.now()
        );
        productRepository.save(product);

        Review review = Review.siteDraft(productId, 5, "素晴らしい商品でした", "proof.png", "Tester", true, "user-1");
        review.setStatus(Review.Status.PUBLISHED);
        reviewRepository.save(review);

        mockMvc.perform(get("/api/products/{id}/scores", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(100.0))
                .andExpect(jsonPath("$.rank").value("C"));

        mockMvc.perform(get("/api/products/{id}/reviews", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stars").value(5))
                .andExpect(jsonPath("$[0].text").value("素晴らしい商品でした"));
    }

    @Test
    void handlesNullStarsGracefully() throws Exception {
        UUID productId = UUID.randomUUID();
        Product product = new Product(
                productId,
                "B0NULLSTAR",
                "Null Star Product",
                "Null Star Product",
                "https://example.com/null",
                true,
                Instant.now(),
                Instant.now()
        );
        productRepository.save(product);

        Review review = Review.siteDraft(productId, 0, "NULLスターのレビュー", "proof-null.png", "Nuller", false, "null-user");
        review.setStatus(Review.Status.PUBLISHED);
        review.setStars(null);
        reviewRepository.save(review);

        mockMvc.perform(get("/api/products/{id}/reviews", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].text").value("NULLスターのレビュー"));

        mockMvc.perform(get("/api/products/{id}/scores", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(50.0))
                .andExpect(jsonPath("$.rank").value("B"));
    }
}
