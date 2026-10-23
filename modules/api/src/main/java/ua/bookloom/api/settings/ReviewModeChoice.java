package ua.bookloom.api.settings;

import java.util.Optional;
import ua.bookloom.api.pipeline.ReviewMode;

/** The bridge between the stored review mode and the one the next run reads, for Settings › Automation. */
public interface ReviewModeChoice {

    /**
     * Names the launch variable or property that decides the review mode of this launch.
     *
     * @return {@code BOOKLOOM_REVIEW_MODE} or {@code bookloom.review.mode} when a launch value is set; empty when
     *     the stored choice decides
     */
    Optional<String> launchOverride();

    /**
     * Tells the source that the person stored a new mode, so the next run start reads it.
     *
     * @param mode the non-null mode just written to the settings
     */
    void stored(ReviewMode mode);
}
