package com.hamas.reviewtrust.domain.scoring.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Formats rule outcomes into human readable strings. This helper can be
 * extended to support localization or richer evidence structures.
 */
public final class EvidenceFormatter {
    private EvidenceFormatter() {}

    /**
     * Formats the evidence from the triggered outcomes into a list of strings.
     * If an outcome has no evidence a generic description is generated from
     * the flag name.
     *
     * @param outcomes list of rule outcomes
     * @return list of formatted evidence strings
     */
    public static List<String> format(List<RuleOutcome> outcomes) {
        List<String> list = new ArrayList<>();
        if (outcomes == null) return list;
        for (RuleOutcome out : outcomes) {
            if (!out.isTriggered()) continue;
            String ev = out.getEvidence();
            if (ev == null || ev.isBlank()) {
                ev = out.getFlag().name();
            }
            list.add(ev);
        }
        return list;
    }
}