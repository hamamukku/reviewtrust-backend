# Step 0 Gap Inventory

Initial repository scan identified the following placeholders or stub-style implementations that must be completed as part of the delivery scope:

## Placeholder / Empty Artifacts (<30 bytes)
- `Dockerfile` — currently a comment-only placeholder.
- `README.md` — contains only a heading.
- `src/main/resources/scoring/rules-catalog_ja.json` — empty JSON object.
- `src/main/resources/static/.gitkeep` — ensure actual assets or confirm intentional.
- `src/test/java/com/hamas/reviewtrust/scoring/rules/.gitkeep` — replace with real tests.

## Backend Methods Returning Null or Empty Sentinels
- `src/main/java/com/hamas/reviewtrust/api/publicapi/v1/ProductsQueryController.java`
- `src/main/java/com/hamas/reviewtrust/common/image/ImageVerifier.java`
- `src/main/java/com/hamas/reviewtrust/common/text/TextNormalizer.java`
- `src/main/java/com/hamas/reviewtrust/config/JwtTokenService.java`
- `src/main/java/com/hamas/reviewtrust/config/RequestIdFilter.java`
- `src/main/java/com/hamas/reviewtrust/domain/products/service/ProductService.java`
- `src/main/java/com/hamas/reviewtrust/domain/reviews/ReviewUpsertRepository.java`
- `src/main/java/com/hamas/reviewtrust/domain/reviews/service/ScoreReadService.java`
- `src/main/java/com/hamas/reviewtrust/domain/scoring/engine/ScoreService.java`
- `src/main/java/com/hamas/reviewtrust/domain/scoring/profile/ThresholdProvider.java`
- `src/main/java/com/hamas/reviewtrust/domain/scraping/client/AmazonReviewClient.java`
- `src/main/java/com/hamas/reviewtrust/domain/scraping/filter/ScrapeFilters.java`
- `src/main/java/com/hamas/reviewtrust/domain/scraping/parser/AmazonReviewParser.java`
- `src/main/java/com/hamas/reviewtrust/domain/scraping/service/ScrapingService.java`
- `src/main/java/com/hamas/reviewtrust/domain/scraping/service/AmazonScrapeService.java`

These null/empty returns need to be reviewed and refactored so that the production endpoints satisfy the contract without relying on sentinel values.

## Frontend Items
- `src/components/AdminVisibilityToggle.tsx` returns `null` for non-admin; verify intentional UX.
- `src/features/products/api.ts` uses mock-oriented fallbacks and throws generic errors; must be aligned with live backend endpoints.

## Delivery Artifacts Missing
- `/delivery` bundle not yet generated (compose, env sample, README, openapi, thresholds, proof, last-10-commands).

This file will be updated as gaps are addressed.

## Resolved
- Dockerfile now builds application image (multi-stage).
- threshold handling via ThresholdProvider + updated YAML.

