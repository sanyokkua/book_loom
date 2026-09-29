package ua.bookloom.pipeline.context;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * What the run knows when it drafts a segment; the assembler reads nothing else, so it needs no repository.
 *
 * @param styleSheet the run's style sheet; its text is what the snapshot records
 * @param summary the rolling summary, or null when none exists yet
 * @param precedingCount how many earlier targets to show: 1 on Fast, 2 on Balanced, 3 on Max; never negative
 * @param glossary every glossary entry as the chunk read it
 * @param earlierMaskedTargets the masked forms of the same unit's earlier segments in document order — decided, or
 *     drafted and waiting in the same chunk; empty at a unit's start
 */
public record ContextInputs(
        StyleSheet styleSheet,
        @Nullable String summary,
        int precedingCount,
        List<GlossaryEntry> glossary,
        List<String> earlierMaskedTargets) {

    /** Rejects a missing component or a negative count and copies both lists. */
    public ContextInputs {
        Objects.requireNonNull(styleSheet, "styleSheet");
        if (precedingCount < 0) {
            throw new IllegalArgumentException("precedingCount must not be negative: " + precedingCount);
        }
        glossary = List.copyOf(Objects.requireNonNull(glossary, "glossary"));
        earlierMaskedTargets = List.copyOf(Objects.requireNonNull(earlierMaskedTargets, "earlierMaskedTargets"));
    }
}
