package ua.bookloom.api.pipeline;

import java.time.Duration;
import java.util.Objects;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;

/**
 * What a names pass did.
 *
 * @param names what the pass added, removed and rewrote in the glossary, against the glossary as it was before the pass
 * @param terms what the pass added, removed and rewrote in the recurring terms, against the list as it was before
 * @param calls how many model calls the pass made, a call sent again after a timeout counted once; never negative
 * @param elapsed how long the pass took, by the pass's clock; never negative
 */
public record NamesPassReport(
        EntryChanges<GlossaryEntry> names, EntryChanges<LexiconEntry> terms, int calls, Duration elapsed) {

    /** Rejects a missing part or a negative count or time. */
    public NamesPassReport {
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(terms, "terms");
        Objects.requireNonNull(elapsed, "elapsed");
        if (calls < 0 || elapsed.isNegative()) {
            throw new IllegalArgumentException("calls and elapsed must not be negative");
        }
    }
}
