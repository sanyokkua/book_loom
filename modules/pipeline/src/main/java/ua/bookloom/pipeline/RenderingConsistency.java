package ua.bookloom.pipeline;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import ua.bookloom.api.project.LexiconEntry;

/**
 * How well a book kept one rendering per recurring term: the mean number of distinct verified renderings over the terms
 * the drafts used at all. 1.0 is a book where every recurring term was written one way; a model left to itself drifts
 * above it. Computed from the lexicon's counts, so a run, the report and the eval harness read the same figure.
 *
 * @param terms how many terms in the lexicon
 * @param used how many of them had a verified rendering
 * @param renderings the verified renderings over those terms
 * @param conflicted how many terms had more than one rendering
 */
public record RenderingConsistency(int terms, int used, int renderings, int conflicted) {

    /**
     * Measures a lexicon.
     *
     * @param entries the non-null entries
     * @return the figures; {@link #distinctPerTerm()} is 0 when no term was used
     */
    public static RenderingConsistency of(final List<LexiconEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        final List<LexiconEntry> seen =
                entries.stream().filter(entry -> entry.distinctRenderings() > 0).toList();
        final int renderings =
                seen.stream().mapToInt(LexiconEntry::distinctRenderings).sum();
        final int conflicted = (int)
                seen.stream().filter(entry -> entry.distinctRenderings() > 1).count();
        return new RenderingConsistency(entries.size(), seen.size(), renderings, conflicted);
    }

    /**
     * The metric: distinct renderings per recurring term used.
     *
     * @return {@code renderings / used}, or 0 when no term was used
     */
    public double distinctPerTerm() {
        return used == 0 ? 0 : (double) renderings / used;
    }

    /** The figures as one log or report line. */
    public String describe() {
        return String.format(
                Locale.ROOT,
                "lexiconTerms=%d used=%d distinctRenderingsPerTerm=%.2f conflicted=%d",
                terms,
                used,
                distinctPerTerm(),
                conflicted);
    }
}
