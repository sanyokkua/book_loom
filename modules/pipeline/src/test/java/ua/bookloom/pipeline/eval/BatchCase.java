package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A batch draft of the real-run corpus, with scripted replies of the shapes the real run produced.
 *
 * @param id the case id
 * @param kind {@code batch-terms} for a batch that asks the model for the renderings it used, {@code batch-context}
 *     for one that also carries earlier pairs, a summary and a lexicon block
 * @param chunk the batch's masked sources, in order; they become the ids 1, 2, …
 * @param glossary the glossary the case holds
 * @param context earlier pairs, summary and the lexicon
 * @param expectTerms whether the batch must ask for terms: its key terms must not be empty
 * @param replies the scripted reply shapes and what the production parser must make of each
 * @param rationale why the case exists
 */
record BatchCase(
        String id,
        String kind,
        List<String> chunk,
        List<EvalTerm> glossary,
        @Nullable EvalContext context,
        boolean expectTerms,
        List<Reply> replies,
        String rationale) {

    /**
     * One reply shape.
     *
     * @param shape correct, a leak of the terms JSON into a target, a code fence in a target, or a too-short target
     * @param reply the reply text a model would send
     * @param expectAccepted whether every id of the reply must be accepted by the production parser and validator
     * @param knownFailure whether production code accepts or rejects the reply wrongly today
     * @param fixedBy the task that fixes a known failure
     */
    record Reply(
            String shape,
            String reply,
            boolean expectAccepted,
            boolean knownFailure,
            @Nullable String fixedBy) {}

    /** Rejects missing parts and copies the lists. */
    BatchCase {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        chunk = List.copyOf(chunk);
        glossary = glossary == null ? List.of() : List.copyOf(glossary);
        replies = List.copyOf(replies);
    }

    EvalContext surroundings() {
        return context == null ? EvalContext.none() : context;
    }

    /** The project setup: the whole chunk is one batch. */
    EvalProject.Setup setup(final String sourceLanguage, final String targetLanguage) {
        return new EvalProject.Setup(sourceLanguage, targetLanguage, null, glossary, surroundings(), chunk, List.of());
    }
}
