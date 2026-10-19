package ua.bookloom.api.pipeline;

import java.time.Duration;
import java.util.List;
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
 * @param untargeted the terms of the glossary that still have no target after the pass, for the person to give; never
 *     null, empty when every name has one
 */
public record NamesPassReport(
        EntryChanges<GlossaryEntry> names,
        EntryChanges<LexiconEntry> terms,
        int calls,
        Duration elapsed,
        List<String> untargeted) {

    /**
     * A report of a pass that left no name without a target.
     *
     * @param names what the pass did to the glossary
     * @param terms what the pass did to the recurring terms
     * @param calls how many model calls the pass made
     * @param elapsed how long the pass took
     */
    public NamesPassReport(
            final EntryChanges<GlossaryEntry> names,
            final EntryChanges<LexiconEntry> terms,
            final int calls,
            final Duration elapsed) {
        this(names, terms, calls, elapsed, List.of());
    }

    /** Rejects a missing part or a negative count or time. */
    public NamesPassReport {
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(terms, "terms");
        Objects.requireNonNull(elapsed, "elapsed");
        untargeted = List.copyOf(Objects.requireNonNull(untargeted, "untargeted"));
        if (calls < 0 || elapsed.isNegative()) {
            throw new IllegalArgumentException("calls and elapsed must not be negative");
        }
    }
}
