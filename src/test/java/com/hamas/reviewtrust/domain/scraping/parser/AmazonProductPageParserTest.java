package com.hamas.reviewtrust.domain.scraping.parser;

import com.hamas.reviewtrust.domain.scraping.model.ProductPageSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AmazonProductPageParserTest {

    private AmazonProductPageParser parser;
    private String sampleHtml;

    @BeforeEach
    void setUp() throws Exception {
        parser = new AmazonProductPageParser();
        sampleHtml = Files.readString(Path.of("data/samples/amazon/product-page.html"));
        assertNotNull(sampleHtml);
        assertFalse(sampleHtml.isBlank());
    }

    @Test
    void parsesCoreFieldsFromSample() {
        ProductPageSnapshot snapshot = parser.parse(sampleHtml);

        assertEquals("B0F3G57FFZ", snapshot.getAsin());
        assertNotNull(snapshot.getTitle());
        assertTrue(snapshot.getTitle().contains("25mg"));
        assertEquals("DUEN", snapshot.getBrand());
        assertEquals(621L, snapshot.getPriceMinor());
        assertEquals(4.2, snapshot.getRatingAverage(), 0.05);
        assertEquals(54L, snapshot.getRatingCount());

        Map<Integer, Double> shares = snapshot.getRatingSharePct();
        assertEquals(5, shares.size());
        assertEquals(52.0, shares.get(5), 0.1);
        assertEquals(26.0, shares.get(4), 0.1);
        assertEquals(16.0, shares.get(3), 0.1);
        assertEquals(3.0, shares.get(2), 0.1);
        assertEquals(3.0, shares.get(1), 0.1);

        assertFalse(snapshot.getImageUrls().isEmpty());
        assertFalse(snapshot.getFeatureBullets().isEmpty());

        assertFalse(snapshot.getInlineReviews().isEmpty());
        ProductPageSnapshot.InlineReview first = snapshot.getInlineReviews().getFirst();
        assertNotNull(first.getTitle());
        assertNotNull(first.getBody());
        assertNotNull(first.getStars());
        assertTrue(first.getStars() >= 1 && first.getStars() <= 5);
        assertNotNull(first.getDateText());

        assertFalse(snapshot.isPartial());
    }
}

