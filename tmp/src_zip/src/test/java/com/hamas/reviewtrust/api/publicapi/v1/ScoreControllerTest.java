package com.hamas.reviewtrust.api.publicapi.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hamas.reviewtrust.domain.reviews.entity.ReviewScore;
import com.hamas.reviewtrust.domain.reviews.entity.ReviewScore.Rank;
import com.hamas.reviewtrust.domain.reviews.entity.ReviewScore.Scope;
import com.hamas.reviewtrust.domain.reviews.repo.ReviewScoreRepository;
import com.hamas.reviewtrust.domain.reviews.service.ScoreReadService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for reading product scores. Since a public ScoreController is not
 * provided in this context, we exercise the underlying ScoreReadService to
 * ensure that the latest scores per scope are returned correctly.
 */
public class ScoreControllerTest {

    @Test
    void latestByProductReturnsBothScopesWhenPresent() {
        // Arrange repositories and service
        ReviewScoreRepository repo = mock(ReviewScoreRepository.class);
        ObjectMapper om = new ObjectMapper();
        ScoreReadService svc = new ScoreReadService(repo, om);

        UUID pid = UUID.randomUUID();
        // Prepare score objects
        ReviewScore amazon = ReviewScore.ofProduct(pid, Scope.AMAZON, 80, Rank.B, null, null, null);
        ReviewScore site   = ReviewScore.ofProduct(pid, Scope.SITE,   90, Rank.C, null, null, null);
        when(repo.findTop1ByProductIdAndScopeOrderByCreatedAtDesc(pid, Scope.AMAZON))
                .thenReturn(Optional.of(amazon));
        when(repo.findTop1ByProductIdAndScopeOrderByCreatedAtDesc(pid, Scope.SITE))
                .thenReturn(Optional.of(site));

        // Act
        ScoreReadService.ProductScores result = svc.latestByProduct(pid);

        // Assert
        assertNotNull(result);
        assertNotNull(result.amazon());
        assertNotNull(result.user());
        assertEquals(80, result.amazon().score());
        assertEquals("B", result.amazon().rank());
        assertEquals(90, result.user().score());
        assertEquals("C", result.user().rank());
    }

    @Test
    void latestByProductHandlesMissingScores() {
        ReviewScoreRepository repo = mock(ReviewScoreRepository.class);
        ObjectMapper om = new ObjectMapper();
        ScoreReadService svc = new ScoreReadService(repo, om);
        UUID pid = UUID.randomUUID();
        // Only Amazon score exists
        ReviewScore amazon = ReviewScore.ofProduct(pid, Scope.AMAZON, 70, Rank.A, null, null, null);
        when(repo.findTop1ByProductIdAndScopeOrderByCreatedAtDesc(pid, Scope.AMAZON))
                .thenReturn(Optional.of(amazon));
        when(repo.findTop1ByProductIdAndScopeOrderByCreatedAtDesc(pid, Scope.SITE))
                .thenReturn(Optional.empty());

        ScoreReadService.ProductScores result = svc.latestByProduct(pid);
        assertNotNull(result);
        assertNotNull(result.amazon());
        assertNull(result.user());
        assertEquals(70, result.amazon().score());
        assertEquals("A", result.amazon().rank());
    }
}
