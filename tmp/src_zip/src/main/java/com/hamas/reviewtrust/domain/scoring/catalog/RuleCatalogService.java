package com.hamas.reviewtrust.domain.scoring.catalog;

import com.hamas.reviewtrust.domain.scoring.engine.Rule;
import com.hamas.reviewtrust.domain.scoring.rules.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides a catalogue of all available scoring rules. Rules are stateless and
 * can be safely reused across requests.
 */
public class RuleCatalogService {
    private final List<Rule> rules;

    public RuleCatalogService() {
        rules = new ArrayList<>();
        rules.add(new DuplicateTextRule());
        rules.add(new UnnaturalJaRule());
        rules.add(new StarDistributionRule());
        rules.add(new ExcessiveFiveRule());
        rules.add(new SurgeRule());
        rules.add(new NoiseRule());
        rules.add(new VerifiedPurchaseRateRule());
        rules.add(new ReviewerAnonymousRule());
        rules.add(new NewAccountBurstRule());
        rules.add(new SameVendorConcentrationRule());
        rules.add(new SpecificityRule());
    }

    /**
     * Returns an immutable list of all rules.
     *
     * @return list of rule instances
     */
    public List<Rule> getRules() {
        return List.copyOf(rules);
    }
}