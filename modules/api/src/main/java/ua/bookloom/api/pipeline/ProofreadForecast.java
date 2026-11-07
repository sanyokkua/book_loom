package ua.bookloom.api.pipeline;

import java.util.Objects;

/**
 * Sent once when a run starts, so the progress can count the Proofread stage from Start (design D15 "Estimate"): how
 * the dial reads the decided text, and how many windows Max's whole-book round will read.
 *
 * @param coverage which decided segments the Proofread stage reads at the run's quality
 * @param wholeBookWindows the windows of Max's second round, the story paragraphs over the batch size rounded up; zero
 *     below Max
 */
public record ProofreadForecast(ProofreadCoverage coverage, int wholeBookWindows) implements JobEvent {

    /** Rejects a missing coverage and a negative count. */
    public ProofreadForecast {
        Objects.requireNonNull(coverage, "coverage");
        if (wholeBookWindows < 0) {
            throw new IllegalArgumentException("wholeBookWindows must not be negative: " + wholeBookWindows);
        }
    }
}
