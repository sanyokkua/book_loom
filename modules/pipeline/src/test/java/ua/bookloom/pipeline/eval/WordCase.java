package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Objects;

/**
 * One labelled case of the garbled-word corpus ({@code eval/words.json}), in the shape of a {@link DefectCase} with the
 * words that are the defect.
 *
 * @param id the case id
 * @param kind the defect family, always {@code garbled}
 * @param source the English source sentence
 * @param candidate the Ukrainian sentence the check reads
 * @param defective whether the candidate holds a garbled word
 * @param words the garbled words, as written in the candidate; empty for a clean case
 * @param rationale why the label is what it is
 */
record WordCase(
        String id,
        String kind,
        String source,
        String candidate,
        boolean defective,
        List<String> words,
        String rationale) {

    /** Rejects missing parts. */
    WordCase {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(rationale, "rationale");
        words = List.copyOf(words);
    }
}
