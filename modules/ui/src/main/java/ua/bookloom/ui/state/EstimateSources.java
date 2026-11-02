package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.api.persistence.ModelSettingsRepository;
import ua.bookloom.api.pipeline.DialProfiles;

/**
 * Where the start estimate takes its figures from.
 *
 * @param stored where the measured speed of a model is kept
 * @param speeds told when a speed was stored, so the estimate is worked out again
 * @param profiles what each quality does, which gives the chunks and the review passes the plan makes
 */
public record EstimateSources(ModelSettingsRepository stored, ModelSpeeds speeds, DialProfiles profiles) {

    /** Rejects a missing source. */
    @Inject
    public EstimateSources {
        Objects.requireNonNull(stored, "stored");
        Objects.requireNonNull(speeds, "speeds");
        Objects.requireNonNull(profiles, "profiles");
    }
}
