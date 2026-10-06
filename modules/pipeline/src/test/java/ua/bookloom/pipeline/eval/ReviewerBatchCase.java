package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A reviewer call over several pairs, as a run sends a chunk: the glossary names, the lexicon renderings, the
 * characters and the narrator the case states, each pair labelled defective or clean.
 *
 * @param id the case id
 * @param kind {@code reviewer-batch}, or {@code reviewer-long} for the batch of long paragraphs that tests the reply cap
 * @param isLong whether the batch is meant to strain the reviewer's reply cap
 * @param glossary the glossary the case holds
 * @param context the lexicon and the narrator
 * @param pairs the pairs in document order
 * @param rationale why the case exists
 */
record ReviewerBatchCase(
        String id,
        String kind,

        @com.fasterxml.jackson.annotation.JsonProperty("long")
        boolean isLong,

        List<EvalTerm> glossary,
        @Nullable EvalContext context,
        List<Pair> pairs,
        String rationale) {

    /**
     * One pair of the batch.
     *
     * @param source the masked English source
     * @param candidate the Ukrainian candidate shown to the reviewer
     * @param defective whether the reviewer must ask for a change
     * @param note what is wrong, for the report; empty for a clean pair
     */
    record Pair(String source, String candidate, boolean defective, String note) {}

    /** Rejects missing parts and copies the lists. */
    ReviewerBatchCase {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        glossary = glossary == null ? List.of() : List.copyOf(glossary);
        pairs = List.copyOf(pairs);
    }

    EvalContext surroundings() {
        return context == null ? EvalContext.none() : context;
    }

    EvalProject.Setup setup(final String sourceLanguage, final String targetLanguage) {
        return new EvalProject.Setup(
                sourceLanguage,
                targetLanguage,
                null,
                glossary,
                surroundings(),
                pairs.stream().map(Pair::source).toList(),
                List.of());
    }
}
