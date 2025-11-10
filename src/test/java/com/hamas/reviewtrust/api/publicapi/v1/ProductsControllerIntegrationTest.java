package com.hamas.reviewtrust.api.publicapi.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hamas.reviewtrust.domain.products.entity.Product;
import com.hamas.reviewtrust.domain.products.service.ProductService;
import com.hamas.reviewtrust.domain.scraping.service.ScrapingService;
import com.hamas.reviewtrust.domain.scoring.engine.ScoreService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ProductsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    @MockBean
    private ScrapingService scrapingService;

    @MockBean
    private ScoreService scoreService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void postProductsReturnsRegistrationResponse() throws Exception {
        String asin = "B0DKF844F3";
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Product product = new Product(id, asin, "Sample Name", "Sample Title",
                "https://www.amazon.co.jp/dp/" + asin, true, now, now);

        ProductService.RegistrationResult registrationResult =
                new ProductService.RegistrationResult(product, true, true);
        String url = "https://www.amazon.co.jp/dp/" + asin;
        when(productService.register(eq(url))).thenReturn(registrationResult);

        when(scrapingService.rescrape(eq(id.toString()), eq(product.getUrl()), anyInt()))
                .thenReturn(ScrapingService.Result.success(id.toString(), product.getUrl(), 12, 10, 1500L, "QUEUED"));

        Map<String, String> payload = Map.of("url", url);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.id").value(id.toString()))
                .andExpect(jsonPath("$.product.asin").value(asin))
                .andExpect(jsonPath("$.product.title").value("Sample Title"))
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.updated").value(true))
                .andExpect(jsonPath("$.scrape.enqueued").value(true));

        verify(productService).register(eq(url));
        verify(scrapingService).rescrape(eq(id.toString()), eq(product.getUrl()), anyInt());
    }
}
