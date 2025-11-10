package com.hamas.reviewtrust;

import com.hamas.reviewtrust.config.AmazonScrapingProperties;
import com.hamas.reviewtrust.config.ScrapingProperties;
import com.hamas.reviewtrust.scraping.ScrapingProps;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ScrapingProps.class, ScrapingProperties.class, AmazonScrapingProperties.class})
public class ReviewTrustApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReviewTrustApplication.class, args);
    }
}
