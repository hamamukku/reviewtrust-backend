package com.hamas.reviewtrust.api.publicapi.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hamas.reviewtrust.domain.products.entity.Product;
import com.hamas.reviewtrust.domain.products.repo.ProductRepository;
import com.hamas.reviewtrust.domain.reviews.entity.Review;
import com.hamas.reviewtrust.domain.reviews.repo.ReviewRepository;
import com.hamas.reviewtrust.domain.scraping.model.ProductPageSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.closeTo;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() throws Exception {
        prepareScoreSchema();
        jdbcTemplate.update("delete from product_snapshots");
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
        insertSnapshot(product, 4.8d, 1L, Map.of(5, 100.0d));

        Review review = Review.siteDraft(productId, 5, "素晴らしい商品でした", "proof.png", "Tester", true, "user-1");
        review.setStatus(Review.Status.APPROVED);
        reviewRepository.save(review);

        mockMvc.perform(get("/api/products/{id}/scores", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId.toString()))
                .andExpect(jsonPath("$.overall.score", closeTo(92.0d, 0.001d)))
                .andExpect(jsonPath("$.overall.rank").value("C"));

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
        insertSnapshot(product, 3.0d, 1L, Map.of(3, 100.0d));

        Review review = Review.siteDraft(productId, 0, "NULLスターのレビュー", "proof-null.png", "Nuller", false, "null-user");
        review.setStatus(Review.Status.APPROVED);
        review.setStars(null);
        reviewRepository.save(review);

        mockMvc.perform(get("/api/products/{id}/reviews", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].text").value("NULLスターのレビュー"));

        mockMvc.perform(get("/api/products/{id}/scores", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId.toString()))
                .andExpect(jsonPath("$.overall.score", closeTo(57.0d, 0.001d)))
                .andExpect(jsonPath("$.overall.rank").value("B"));
    }

    private void prepareScoreSchema() {
        jdbcTemplate.execute("alter table reviews add column if not exists rating integer");
        jdbcTemplate.execute("alter table reviews add column if not exists fingerprint varchar(128)");
        jdbcTemplate.execute("""
                create table if not exists product_snapshots (
                    id uuid primary key,
                    product_id uuid not null,
                    product_name varchar(255),
                    source_url varchar(2048),
                    source_html clob,
                    snapshot_json clob not null,
                    upload_target varchar(255),
                    uploaded_at timestamp with time zone,
                    created_at timestamp with time zone not null
                )
                """);
    }

    private void insertSnapshot(Product product,
                                double ratingAverage,
                                long ratingCount,
                                Map<Integer, Double> ratingSharePct) throws Exception {
        ProductPageSnapshot snapshot = ProductPageSnapshot.builder()
                .asin(product.getAsin())
                .title(product.getTitle())
                .ratingAverage(ratingAverage)
                .ratingCount(ratingCount)
                .ratingSharePct(ratingSharePct)
                .capturedAt(Instant.now())
                .build();

        jdbcTemplate.update("""
                        insert into product_snapshots (
                            id, product_id, product_name, source_url, source_html, snapshot_json, upload_target, uploaded_at, created_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                product.getId(),
                product.getTitle(),
                product.getUrl(),
                "",
                objectMapper.writeValueAsString(snapshot),
                null,
                null,
                Instant.now());
    }
}
