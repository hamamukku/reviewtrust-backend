package com.hamas.reviewtrust.domain.scoring.profile;

/**
 * Repository responsible for providing scoring profiles. In a production
 * system this might load configuration from a database or external YAML
 * file. Here it returns a singleton default profile.
 */
public class ScoringProfileRepository {
    private final ScoringProfile defaultProfile = new ScoringProfile();

    /**
     * Returns the default scoring profile. Callers should not mutate the
     * returned object unless they intend to share those changes globally.
     *
     * @return default profile
     */
    public ScoringProfile get() {
        return defaultProfile;
    }
}