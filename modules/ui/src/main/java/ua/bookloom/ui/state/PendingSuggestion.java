package ua.bookloom.ui.state;

import java.util.Objects;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.project.BookBrief;

/**
 * A suggestion the model made that is held until the person applies it.
 *
 * @param suggestion what the model decided, with its evidence
 * @param basis the brief as it was when the answer arrived; Apply merges against the brief as it is then, not this one
 */
public record PendingSuggestion(BriefSuggestion suggestion, BookBrief basis) {

    /** Rejects a missing part. */
    public PendingSuggestion {
        Objects.requireNonNull(suggestion, "suggestion");
        Objects.requireNonNull(basis, "basis");
    }
}
