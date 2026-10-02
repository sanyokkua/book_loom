package ua.bookloom.api.pipeline;

import java.util.List;
import java.util.Objects;
import ua.bookloom.api.project.GlossaryEntry;

/**
 * What a model review of the glossary changed.
 *
 * @param removed how many terms the model judged not a name and were removed; never negative
 * @param updated how many terms took the model's type or gender; never negative
 * @param suggested how many terms received a suggested target; never negative
 * @param entries the glossary as the review left it, in its stored order
 */
public record GlossaryReviewReport(int removed, int updated, int suggested, List<GlossaryEntry> entries) {

    /** Validates the counts and copies the entries. */
    public GlossaryReviewReport {
        if (removed < 0 || updated < 0 || suggested < 0) {
            throw new IllegalArgumentException("counts must not be negative");
        }
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }

    /** A review that suggested no target. */
    public GlossaryReviewReport(final int removed, final int updated, final List<GlossaryEntry> entries) {
        this(removed, updated, 0, entries);
    }
}
