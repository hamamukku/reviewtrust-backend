package com.hamas.reviewtrust.scoring.engine;

import com.hamas.reviewtrust.domain.scoring.catalog.ScoreModels;
import com.hamas.reviewtrust.domain.scoring.engine.Ranker;
import com.hamas.reviewtrust.domain.scoring.profile.ThresholdProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RankerTest {

    @Test
    void assignUsesNewBoundaries() {
        assertEquals(ScoreModels.Rank.A, Ranker.assign(0));
        assertEquals(ScoreModels.Rank.A, Ranker.assign(34));
        assertEquals(ScoreModels.Rank.B, Ranker.assign(35));
        assertEquals(ScoreModels.Rank.B, Ranker.assign(64));
        assertEquals(ScoreModels.Rank.C, Ranker.assign(65));
        assertEquals(ScoreModels.Rank.C, Ranker.assign(100));
    }

    @Test
    void judgeSakuraFollowsThresholds() {
        ThresholdProvider.Thresholds thresholds = ThresholdProvider.Thresholds.defaults();

        ScoreModels.FeatureSnapshot sakura = new ScoreModels.FeatureSnapshot(0.85, 0.60, 0.0, 0.0);
        assertEquals(ScoreModels.SakuraJudge.SAKURA, Ranker.judgeSakura(sakura, thresholds));

        ScoreModels.FeatureSnapshot likely = new ScoreModels.FeatureSnapshot(0.68, 0.20, 0.0, 0.0);
        assertEquals(ScoreModels.SakuraJudge.LIKELY, Ranker.judgeSakura(likely, thresholds));

        ScoreModels.FeatureSnapshot unlikely = new ScoreModels.FeatureSnapshot(0.50, 0.10, 0.0, 0.0);
        assertEquals(ScoreModels.SakuraJudge.UNLIKELY, Ranker.judgeSakura(unlikely, thresholds));

        ScoreModels.FeatureSnapshot genuine = new ScoreModels.FeatureSnapshot(0.30, 0.05, 0.0, 0.0);
        assertEquals(ScoreModels.SakuraJudge.GENUINE, Ranker.judgeSakura(genuine, thresholds));
    }
}
