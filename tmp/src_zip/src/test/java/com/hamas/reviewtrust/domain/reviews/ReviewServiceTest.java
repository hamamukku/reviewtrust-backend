package com.hamas.reviewtrust.domain.reviews;

import com.hamas.reviewtrust.domain.products.entity.Product;
import com.hamas.reviewtrust.domain.products.repo.ProductRepository;
import com.hamas.reviewtrust.domain.reviews.entity.Review;
import com.hamas.reviewtrust.domain.reviews.repo.ReviewRepository;
import com.hamas.reviewtrust.domain.reviews.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReviewService covering draft submission and published listing.
 */
public class ReviewServiceTest {
    private ReviewRepository reviewRepo;
    private ProductRepository productRepo;
    private ReviewService service;

    @BeforeEach
    void setup() {
        reviewRepo = mock(ReviewRepository.class);
        productRepo = mock(ProductRepository.class);
        service = new ReviewService(reviewRepo, productRepo);
    }

    @Test
    void submitSiteDraftStoresDraftWhenValid() {
        UUID productId = UUID.randomUUID();
        // stub product exists
        Product p = new Product(productId, "ASIN123", "Product", "http://example.com", true, Instant.now(), Instant.now());
        when(productRepo.findById(productId)).thenReturn(Optional.of(p));
        // capture saved review
        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        when(reviewRepo.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        Review r = service.submitSiteDraft(productId, 5, "Great product", "/path/proof.jpg",
                "Alice", true, "user123");

        assertNotNull(r);
        assertEquals(Review.Source.SITE, r.getSource());
        assertEquals(Review.Status.DRAFT, r.getStatus());
        assertEquals(5, r.getStars());
        assertEquals("Great product", r.getText());
        assertTrue(r.isVerifiedPurchase());
        assertTrue(r.isHasImage());
        // ensure repo.save was called
        verify(reviewRepo, times(1)).save(any(Review.class));
    }

    @Test
    void submitSiteDraftRejectsInvalidStars() {
        UUID productId = UUID.randomUUID();
        when(productRepo.findById(productId)).thenReturn(Optional.of(mock(Product.class)));
        assertThrows(ResponseStatusException.class, () ->
                service.submitSiteDraft(productId, 6, "text", "/proof", null, null, null));
        assertThrows(ResponseStatusException.class, () ->
                service.submitSiteDraft(productId, 0, "text", "/proof", null, null, null));
    }

    @Test
    void submitSiteDraftRejectsMissingTextOrProof() {
        UUID productId = UUID.randomUUID();
        when(productRepo.findById(productId)).thenReturn(Optional.of(mock(Product.class)));
        assertThrows(ResponseStatusException.class, () ->
                service.submitSiteDraft(productId, 3, "", "/proof", null, null, null));
        assertThrows(ResponseStatusException.class, () ->
                service.submitSiteDraft(productId, 3, "valid", "", null, null, null));
    }

    @Test
    void listPublishedReturnsLatestReviews() {
        UUID productId = UUID.randomUUID();
        when(productRepo.findById(productId)).thenReturn(Optional.of(mock(Product.class)));
        Review r1 = Review.siteDraft(productId, 5, "text1", "/proof1", "Alice", true, null);
        r1.setStatus(Review.Status.PUBLISHED);
        Review r2 = Review.siteDraft(productId, 4, "text2", "/proof2", "Bob", null, null);
        r2.setStatus(Review.Status.PUBLISHED);
        // simulate ordering by createdAt desc: r2 is newer
        List<Review> list = List.of(r2, r1);
        when(reviewRepo.findByProductIdAndSourceAndStatus(eq(productId), eq(Review.Source.SITE), eq(Review.Status.PUBLISHED),
                any(PageRequest.class))).thenReturn(new PageImpl<>(list));

        List<Review> out = service.listPublished(productId, Review.Source.SITE, 10);
        assertEquals(2, out.size());
        assertEquals("text2", out.get(0).getText());
        assertEquals("text1", out.get(1).getText());
    }
}