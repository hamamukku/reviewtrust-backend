// ScoringProfileController.java (placeholder)
package com.hamas.reviewtrust.api.admin.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.yaml.snakeyaml.Yaml;
import org.springframework.http.HttpStatus;

import java.io.InputStream;
import java.util.Map;

/**
 * スコア閾値/ルールカタログの“参照専用”API（MVP）。
 * - GET /api/admin/scoring/profile         → scoring/thresholds.yml を Map化して返却
 * - GET /api/admin/scoring/rules-catalog   → scoring/rules-catalog_ja.json をJSONで返却
 * 仕様の「rules/evidence 公開」とファイルツリーの配置に基づく読み出し専用口。 
 */
@RestController
@RequestMapping("/api/admin/scoring")
public class ScoringProfileController {

    private final ObjectMapper om;

    public ScoringProfileController(ObjectMapper om) {
        this.om = om;
    }

    @GetMapping(value = "/profile", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> profile() {
        try (InputStream in = new ClassPathResource("scoring/thresholds.yml").getInputStream()) {
            Object data = new Yaml().load(in);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (data instanceof Map<?, ?> m) ? (Map<String, Object>) m : Map.of();
            return map;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to load thresholds.yml");
        }
    }

    @GetMapping(value = "/rules-catalog", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> rulesCatalog() {
        try (InputStream in = new ClassPathResource("scoring/rules-catalog_ja.json").getInputStream()) {
            JsonNode node = om.readTree(in);
            return ResponseEntity.ok(node);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to load rules-catalog_ja.json");
        }
    }
}

