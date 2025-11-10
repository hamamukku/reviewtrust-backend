// src/main/java/com/hamas/reviewtrust/domain/scoring/catalog/DefaultRuleCatalogService.java
package com.hamas.reviewtrust.domain.scoring.catalog;

import com.hamas.reviewtrust.domain.scoring.engine.Rule;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 既定のルールカタログ実装。
 * Spring に登録された Rule の Bean を束ね、ScoringEngine に提供する。
 * ※ RuleCatalogService が class のため extends で提供する。
 */
@Component
@Primary
public class DefaultRuleCatalogService extends RuleCatalogService {

    private final List<Rule> rules;

    public DefaultRuleCatalogService(List<Rule> rules) {
        this.rules = (rules == null) ? List.of() : List.copyOf(rules);
    }

    @Override
    public List<Rule> getRules() {
        return rules;
    }
}
