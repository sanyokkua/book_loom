package ua.bookloom.pipeline;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import ua.bookloom.api.project.LexiconEntry;

/**
 * How well a book kept one rendering per recurring term: the mean number of distinct renderings over the terms that were
 * used at all, either reported by a draft and verified, or learned from the decided segments by co-occurrence. 1.0 is
 * a book where every recurring term was written one way; a model left to itself drifts above it. A term with only a
 * learned rendering counts as one rendering, since learning establishes a rendering only where one clearly dominates;
 * {@link #learnedCoverage()} then says how much of the term's occurrences carry it. Computed from the lexicon's counts,
 * so a run, the report and the eval harness read the same figure.
 *
 * @param terms how many terms in the lexicon
 * @param used how many of them had a verified or learned rendering
 * @param renderings the renderings over those terms
 * @param conflicted how many terms had more than one verified rendering
 * @param learned how many terms have a rendering learned by co-occurrence
 * @param learnedSupport the segments carrying the learned rendering, summed over the learned terms
 * @param learnedOccurrences the segments naming the term, summed over the learned terms
 */
public record RenderingConsistency(
        int terms, int used, int renderings, int conflicted, int learned, int learnedSupport, int learnedOccurrences) {

    /**
     * Measures a lexicon.
     *
     * @param entries the non-null entries
     * @return the figures; {@link #distinctPerTerm()} is 0 when no term was used
     */
    public static RenderingConsistency of(final List<LexiconEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        final List<LexiconEntry> seen =
                entries.stream().filter(entry -> distinct(entry) > 0).toList();
        final List<LexiconEntry.Learned> learned = entries.stream()
                .map(LexiconEntry::learned)
                .filter(Objects::nonNull)
                .toList();
        return new RenderingConsistency(
                entries.size(),
                seen.size(),
                seen.stream().mapToInt(RenderingConsistency::distinct).sum(),
                (int) seen.stream().filter(entry -> distinct(entry) > 1).count(),
                learned.size(),
                learned.stream().mapToInt(LexiconEntry.Learned::support).sum(),
                learned.stream().mapToInt(LexiconEntry.Learned::occurrences).sum());
    }

    private static int distinct(final LexiconEntry entry) {
        return entry.distinctRenderings() == 0 && entry.learned() != null ? 1 : entry.distinctRenderings();
    }

    /**
     * The metric: distinct renderings per recurring term used.
     *
     * @return {@code renderings / used}, or 0 when no term was used
     */
    public double distinctPerTerm() {
        return used == 0 ? 0 : (double) renderings / used;
    }

    /**
     * The share of the learned terms' occurrences that carry the learned rendering.
     *
     * @return {@code support / occurrences} over the learned terms, or 0 when none was learned
     */
    public double learnedCoverage() {
        return learnedOccurrences == 0 ? 0 : (double) learnedSupport / learnedOccurrences;
    }

    /** The figures as one log or report line. */
    public String describe() {
        return String.format(
                Locale.ROOT,
                "lexiconTerms=%d used=%d distinctRenderingsPerTerm=%.2f conflicted=%d learned=%d learnedCoverage=%.2f",
                terms,
                used,
                distinctPerTerm(),
                conflicted,
                learned,
                learnedCoverage());
    }
}
