package ua.bookloom.ui.state;

import java.util.Objects;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * The two choices that shape a run and that the title bar names beside its state, so a person who opens a long run
 * on another screen still sees how hard it works and when it will ask for them.
 *
 * @param dial the brief's speed and quality choice the run started with
 * @param reviewMode how much of the outcome the person confirms
 */
public record RunMode(QualityDial dial, ReviewMode reviewMode) {

    /** Rejects a missing component. */
    public RunMode {
        Objects.requireNonNull(dial, "dial");
        Objects.requireNonNull(reviewMode, "reviewMode");
    }

    /**
     * The catalogue entry naming a dial, shared by every place that shows one.
     *
     * @param dial the dial to name; never null
     * @return the key of its label
     */
    public static MessageKey dialKey(final QualityDial dial) {
        return switch (Objects.requireNonNull(dial, "dial")) {
            case FAST -> MessageKey.BRIEF_QUALITY_FAST;
            case BALANCED -> MessageKey.BRIEF_QUALITY_BALANCED;
            case MAX -> MessageKey.BRIEF_QUALITY_MAX;
        };
    }
}
