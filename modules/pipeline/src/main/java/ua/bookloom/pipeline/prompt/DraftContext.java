package ua.bookloom.pipeline.prompt;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What the draft prompt carries besides the segment: the book so far, the names in play, earlier decisions and the
 * neighbouring translated text. An empty part is left out of the prompt entirely.
 *
 * @param precedingTargets the earlier segments' target text of the same unit, in order, as plain text
 * @param summary the rolling summary, or null when none exists yet
 * @param glossaryLines one line per injected glossary term
 * @param memoryLines one {@code source → target} line per translation-memory hint or suggestion
 */
public record DraftContext(
        List<String> precedingTargets, @Nullable String summary, List<String> glossaryLines, List<String> memoryLines) {

    /** Rejects null entries and makes the context immutable at the prompt boundary. */
    public DraftContext {
        precedingTargets = List.copyOf(Objects.requireNonNull(precedingTargets, "precedingTargets"));
        glossaryLines = List.copyOf(Objects.requireNonNull(glossaryLines, "glossaryLines"));
        memoryLines = List.copyOf(Objects.requireNonNull(memoryLines, "memoryLines"));
    }

    /** A context holding only preceding targets. */
    public DraftContext(final List<String> precedingTargets) {
        this(precedingTargets, null, List.of(), List.of());
    }

    /** Returns the empty context used at the start of a section. */
    public static DraftContext empty() {
        return new DraftContext(List.of());
    }
}
