package ua.bookloom.pipeline.glossary;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.pipeline.QualityDial;

/**
 * Decides whether the model name scan is worth its calls. It is a second opinion for a book the code read badly: at
 * Max it always runs, and below Max it runs only when the code's own scan proposes fewer than {@link #MIN_PROPOSALS}
 * names, because a book the code already reads into ten names or more does not need a model to find more.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PreScanGate {

    /** Below Max, the model scan runs only when the code proposes fewer names than this. */
    static final int MIN_PROPOSALS = 10;

    /**
     * Whether the model name scan should run.
     *
     * @param dial the Book Brief's dial; never null
     * @param proposals how many names the code proposes, those written {@link FrequencyScan#PROPOSAL_MIN_COUNT} times
     *     or more once the triage has dropped what is not a name
     * @return {@code true} at Max or when {@code proposals} is under {@link #MIN_PROPOSALS}, {@code false} otherwise
     */
    static boolean isWanted(final QualityDial dial, final int proposals) {
        return dial == QualityDial.MAX || proposals < MIN_PROPOSALS;
    }
}
