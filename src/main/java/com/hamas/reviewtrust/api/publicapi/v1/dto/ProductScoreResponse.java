package com.hamas.reviewtrust.api.publicapi.v1.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductScoreResponse {
    private UUID productId;
    private String asin;
    private String productName;
    private Double averageScore;
    private Integer reviewCount;
    private Map<Integer, Long> histogram = new LinkedHashMap<>();
    private ScoreBlock overall;
    private ScoreBlock amazon;
    private ScoreBlock user;
    @JsonProperty("sakura_judge")
    private String sakuraJudge;
    private List<String> flags = new ArrayList<>();
    private List<RuleEntry> rules = new ArrayList<>();

    public ProductScoreResponse() {
        // for Jackson
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public String getAsin() {
        return asin;
    }

    public void setAsin(String asin) {
        this.asin = asin;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Double getAverageScore() {
        return averageScore;
    }

    public void setAverageScore(Double averageScore) {
        this.averageScore = averageScore;
    }

    public Integer getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(Integer reviewCount) {
        this.reviewCount = reviewCount;
    }

    public Map<Integer, Long> getHistogram() {
        return Collections.unmodifiableMap(histogram);
    }

    public void setHistogram(Map<Integer, Long> histogram) {
        this.histogram = histogram == null ? new LinkedHashMap<>() : new LinkedHashMap<>(histogram);
    }

    public ScoreBlock getOverall() {
        return overall;
    }

    public void setOverall(ScoreBlock overall) {
        this.overall = overall;
        if (overall != null) {
            setSakuraJudge(overall.getSakuraJudge());
            setFlags(overall.getFlags());
            setRules(overall.getRules());
        }
    }

    public ScoreBlock getAmazon() {
        return amazon;
    }

    public void setAmazon(ScoreBlock amazon) {
        this.amazon = amazon;
    }

    public ScoreBlock getUser() {
        return user;
    }

    public void setUser(ScoreBlock user) {
        this.user = user;
    }

    public String getSakuraJudge() {
        return sakuraJudge;
    }

    public void setSakuraJudge(String sakuraJudge) {
        this.sakuraJudge = sakuraJudge;
    }

    public List<String> getFlags() {
        return Collections.unmodifiableList(flags);
    }

    public void setFlags(List<String> flags) {
        this.flags = flags == null ? new ArrayList<>() : new ArrayList<>(flags);
    }

    public List<RuleEntry> getRules() {
        return Collections.unmodifiableList(rules);
    }

    public void setRules(List<RuleEntry> rules) {
        this.rules = rules == null ? new ArrayList<>() : new ArrayList<>(rules);
    }

    public static class ScoreBlock {
        private Double score;
        @JsonProperty("display_score")
        private Double displayScore;
        private String rank;
        @JsonProperty("sakura_judge")
        private String sakuraJudge;
        private List<String> flags = new ArrayList<>();
        private List<RuleEntry> rules = new ArrayList<>();
        private Map<String, Object> metrics = new LinkedHashMap<>();

        public ScoreBlock() {
        }

        public ScoreBlock(Double score, String rank) {
            this.score = score;
            this.rank = rank;
        }

        public ScoreBlock(Double score,
                          Double displayScore,
                          String rank,
                          String sakuraJudge,
                          List<String> flags,
                          List<RuleEntry> rules,
                          Map<String, Object> metrics) {
            this.score = score;
            this.displayScore = displayScore;
            this.rank = rank;
            setSakuraJudge(sakuraJudge);
            setFlags(flags);
            setRules(rules);
            setMetrics(metrics);
        }

        public Double getScore() {
            return score;
        }

        public void setScore(Double score) {
            this.score = score;
        }

        public Double getDisplayScore() {
            return displayScore;
        }

        public void setDisplayScore(Double displayScore) {
            this.displayScore = displayScore;
        }

        public String getRank() {
            return rank;
        }

        public void setRank(String rank) {
            this.rank = rank;
        }

        public String getSakuraJudge() {
            return sakuraJudge;
        }

        public void setSakuraJudge(String sakuraJudge) {
            this.sakuraJudge = sakuraJudge;
        }

        public List<String> getFlags() {
            return Collections.unmodifiableList(flags);
        }

        public void setFlags(List<String> flags) {
            this.flags = flags == null ? new ArrayList<>() : new ArrayList<>(flags);
        }

        public List<RuleEntry> getRules() {
            return Collections.unmodifiableList(rules);
        }

        public void setRules(List<RuleEntry> rules) {
            this.rules = rules == null ? new ArrayList<>() : new ArrayList<>(rules);
        }

        public Map<String, Object> getMetrics() {
            return Collections.unmodifiableMap(metrics);
        }

        public void setMetrics(Map<String, Object> metrics) {
            this.metrics = metrics == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metrics);
        }
    }

    public static class RuleEntry {
        private String id;
        private Double value;
        private Double warn;
        private Double crit;
        private Double weight;
        private Integer points;
        private Map<String, Object> extra = new LinkedHashMap<>();

        public RuleEntry() {
        }

        public RuleEntry(String id,
                         Double value,
                         Double warn,
                         Double crit,
                         Double weight,
                         Integer points,
                         Map<String, Object> extra) {
            this.id = id;
            this.value = value;
            this.warn = warn;
            this.crit = crit;
            this.weight = weight;
            this.points = points;
            setExtra(extra);
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Double getValue() {
            return value;
        }

        public void setValue(Double value) {
            this.value = value;
        }

        public Double getWarn() {
            return warn;
        }

        public void setWarn(Double warn) {
            this.warn = warn;
        }

        public Double getCrit() {
            return crit;
        }

        public void setCrit(Double crit) {
            this.crit = crit;
        }

        public Double getWeight() {
            return weight;
        }

        public void setWeight(Double weight) {
            this.weight = weight;
        }

        public Integer getPoints() {
            return points;
        }

        public void setPoints(Integer points) {
            this.points = points;
        }

        public Map<String, Object> getExtra() {
            return Collections.unmodifiableMap(extra);
        }

        public void setExtra(Map<String, Object> extra) {
            this.extra = extra == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extra);
        }
    }
}
