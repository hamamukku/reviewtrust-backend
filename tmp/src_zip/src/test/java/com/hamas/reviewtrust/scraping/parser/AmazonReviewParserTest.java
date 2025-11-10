package com.hamas.reviewtrust.scraping.parser;

import com.hamas.reviewtrust.domain.scraping.parser.AmazonReviewParser;
import com.hamas.reviewtrust.domain.scraping.parser.AmazonReviewParser.ReviewItem;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

public class AmazonReviewParserTest {

    @Test
    void parse_should_extract_basic_fields_ja() {
        String html = """
                <html><body>
                  <div data-hook="review" id="R1">
                    <span class="a-icon-alt">5つ星のうち5.0</span>
                    <a data-hook="review-title"><span>最高の製品</span></a>
                    <span data-hook="review-date">2024年9月12日</span>
                    <span class="a-profile-name">山田太郎</span>
                    <span data-hook="review-body">とても満足しています。</span>
                    <span data-hook="helpful-vote-statement">12人のお客様がこれが役に立ったと考えています</span>
                  </div>
                  <div data-hook="review" id="R2">
                    <span class="a-icon-alt">5つ星のうち4.0</span>
                    <a data-hook="review-title"><span>まずまず</span></a>
                    <span data-hook="review-date">2024年9月10日</span>
                    <span class="a-profile-name">Suzuki</span>
                    <span data-hook="review-body">価格相応。</span>
                    <span data-hook="helpful-vote-statement">3人</span>
                  </div>
                </body></html>
                """;

        AmazonReviewParser parser = new AmazonReviewParser();
        List<ReviewItem> items = parser.parse(html, Locale.JAPAN, 10, "B0TESTASIN");

        assertEquals(2, items.size());
        ReviewItem first = items.get(0);
        assertEquals("B0TESTASIN", first.getAsin());
        assertEquals("R1", first.getReviewId());
        assertEquals("最高の製品", first.getTitle());
        assertEquals(5, first.getRating());
        assertEquals(LocalDate.of(2024, 9, 12), first.getReviewDate());
        assertEquals("山田太郎", first.getReviewer());
        assertTrue(first.getHelpfulVotes() >= 12);
    }

    @Test
    void parse_should_understand_en_stars() {
        String html = """
                <html><body>
                  <div data-hook="review" id="U1">
                    <i class="a-icon-star"><span class="a-icon-alt">4.0 out of 5 stars</span></i>
                    <a data-hook="review-title"><span>Works good</span></a>
                    <span data-hook="review-date">September 1, 2024</span>
                    <span class="a-profile-name">Alice</span>
                    <span data-hook="review-body">Nice product.</span>
                    <span data-hook="helpful-vote-statement">7 people found this helpful</span>
                  </div>
                </body></html>
                """;

        AmazonReviewParser parser = new AmazonReviewParser();
        List<ReviewItem> items = parser.parse(html, Locale.US, 5, "B0ENASIN");

        assertEquals(1, items.size());
        ReviewItem it = items.get(0);
        assertEquals(4, it.getRating());
        assertEquals("Works good", it.getTitle());
        assertEquals("Alice", it.getReviewer());
        assertNotNull(it.getReviewDate());
    }
}
