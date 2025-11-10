package app.scraper.amazon;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple CLI to parse an Amazon product page (local HTML or HTTP URL) and print the
 * review histogram JSON to stdout. Useful for quick manual verification of selectors.
 */
public final class AmazonReviewScrapeCli {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .findAndRegisterModules();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private AmazonReviewScrapeCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || (!"-f".equals(args[0]) && !"-u".equals(args[0]))) {
            System.err.println("Usage: -f <htmlFile> | -u <url>");
            System.exit(2);
        }

        String html = "-f".equals(args[0]) ? readLocalHtml(args[1]) : fetchHtml(args[1]);
        String cliUrl = "-u".equals(args[0]) ? args[1] : null;
        Document document = Jsoup.parse(html);
        var result = ReviewHistogramParser.parse(document);

        if (sum(result) == 0) {
            try {
                String popoverUrl = tryExtractPopoverUrl(document);
                if (popoverUrl != null) {
                    String resolved = resolve(popoverUrl);
                    if (resolved == null) {
                        System.out.println("[info] fallback: popover resolve failed");
                        throw new IllegalArgumentException("popover url could not be resolved");
                    }
                    String popoverHtml = fetchHtml(resolved);
                    var popResult = ReviewHistogramParser.parse(Jsoup.parse(popoverHtml));
                    if (sum(popResult) > 0) {
                        System.out.println("[info] fallback: popover ok -> histogram recovered");
                        result = popResult;
                    } else {
                        System.out.println("[info] fallback: popover tried -> still 0");
                    }
                } else {
                    System.out.println("[info] fallback: popover url not found");
                }
            } catch (Exception e) {
                System.out.println("[info] fallback: popover error message=" + e.getMessage());
            }
        }

        if (sum(result) == 0) {
            try {
                String asin = tryExtractAsin(document, cliUrl);
                if (asin != null) {
                    String reviewsUrl = "https://www.amazon.co.jp/product-reviews/" + asin + "?language=ja_JP";
                    String reviewsHtml = fetchHtml(reviewsUrl);
                    var reviewsResult = ReviewHistogramParser.parse(Jsoup.parse(reviewsHtml));
                    if (sum(reviewsResult) > 0) {
                        System.out.println("[info] fallback: product-reviews ok -> histogram recovered");
                        result = reviewsResult;
                    } else {
                        System.out.println("[info] fallback: product-reviews tried -> still 0");
                    }
                } else {
                    System.out.println("[info] fallback: asin not found");
                }
            } catch (Exception e) {
                System.out.println("[info] fallback: product-reviews error message=" + e.getMessage());
            }
        }

        String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        System.out.println(json);
    }

    private static String readLocalHtml(String file) throws IOException {
        return Files.readString(Path.of(file), StandardCharsets.UTF_8);
    }

    private static String fetchHtml(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Accept-Language", "ja-JP,ja;q=0.9,en-US;q=0.8,en;q=0.7")
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    private static int sum(ReviewHistogramParser.Result result) {
        return result.percentageByStar.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static String tryExtractPopoverUrl(Document document) {
        Element popover = document.selectFirst("#acrPopover");
        if (popover == null) {
            return null;
        }
        String data = popover.attr("data-a-popover");
        if (data == null || data.isBlank()) {
            return null;
        }
        String unescaped = Parser.unescapeEntities(data, true);
        try {
            JsonNode node = MAPPER.readTree(unescaped);
            JsonNode urlNode = node.get("url");
            if (urlNode != null && !urlNode.asText().isBlank()) {
                return urlNode.asText();
            }
            return null;
        } catch (Exception e) {
            throw new IllegalStateException("failed to parse data-a-popover JSON", e);
        }
    }

    private static String resolve(String href) {
        if (href == null || href.isBlank()) {
            return null;
        }
        if (href.startsWith("http://") || href.startsWith("https://")) {
            return href;
        }
        if (href.startsWith("/")) {
            return "https://www.amazon.co.jp" + href;
        }
        return "https://www.amazon.co.jp/" + href;
    }

    private static String tryExtractAsin(Document document, String cliUrl) {
        for (String selector : List.of("input#ASIN", "meta[name=ASIN]", "[data-asin]")) {
            Element element = document.selectFirst(selector);
            if (element != null) {
                String value = element.hasAttr("value")
                        ? element.attr("value")
                        : element.hasAttr("content")
                        ? element.attr("content")
                        : element.attr("data-asin");
                String asin = normalizeAsin(value);
                if (asin != null) {
                    return asin;
                }
            }
        }

        if (cliUrl != null) {
            Matcher matcher = Pattern.compile("/dp/(B0[0-9A-Z]{8})").matcher(cliUrl);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return null;
    }

    private static String normalizeAsin(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        Matcher matcher = Pattern.compile("(B0[0-9A-Z]{8})").matcher(candidate);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
