package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * What the chapter-summary gate did over one run (arm 6's falsifier: the gate drops more than 20 % of the sentences).
 * Counted over each chapter part's final answer — the answer to the one re-ask when there was one, else the first — so
 * a sentence the re-ask fixed is not counted as dropped; a sentence still marked for a re-ask in a final answer (the
 * re-ask gave nothing) was left out, so it counts as dropped.
 *
 * @param sentences the sentences of the final answers the gate read
 * @param kept how many of them it kept
 * @param decisions per decision of the gate ({@code kept}, {@code cut}, {@code fragment}, {@code reask},
 *     {@code dropped-letters}, {@code dropped-entry}, {@code dropped-markup}) the sentences of the final answers it was taken for
 * @param reasked chapter parts whose first answer was asked once more
 * @param chaptersKept chapters whose summary was kept
 * @param notKept per reason the chapters left without a summary
 */
record SummaryGateTally(
        int sentences,
        int kept,
        Map<String, Integer> decisions,
        int reasked,
        int chaptersKept,
        Map<String, Integer> notKept) {

    /** A run that summarised nothing. */
    static final SummaryGateTally NONE = new SummaryGateTally(0, 0, Map.of(), 0, 0, Map.of());

    /** Copies the maps. */
    SummaryGateTally {
        decisions = Map.copyOf(decisions);
        notKept = Map.copyOf(notKept);
    }

    /** The sentences of the final answers the gate did not keep. */
    @JsonProperty("dropped")
    int dropped() {
        return sentences - kept;
    }

    /** {@code dropped / sentences}, or -1 when the gate read no sentence. */
    @JsonProperty("dropShare")
    double dropShare() {
        return sentences == 0 ? -1 : (double) dropped() / sentences;
    }
}
