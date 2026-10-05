package ua.bookloom.pipeline.context;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
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
 * @param dynamicTokens the tokens the dynamic context may take, from {@link ContextBudget#dynamicAllowance}; the
 *     lowest-priority sections are cut to fit it
 * @param lexicon the project's recurring-term entries as they stand; only the established renderings of terms the
 *     chunk names are shown, and never one the glossary already holds
 */
public record ContextInputs(
        StyleSheet styleSheet,
        @Nullable String summary,
        int precedingCount,
        List<GlossaryEntry> glossary,
        List<String> earlierMaskedTargets,
        int dynamicTokens,
        List<LexiconEntry> lexicon) {

    /** A set of inputs with a window to respect and no lexicon. */
    public ContextInputs(
            final StyleSheet styleSheet,
            @Nullable final String summary,
            final int precedingCount,
            final List<GlossaryEntry> glossary,
            final List<String> earlierMaskedTargets,
            final int dynamicTokens) {
        this(styleSheet, summary, precedingCount, glossary, earlierMaskedTargets, dynamicTokens, List.of());
    }

    /** A set of inputs with no limit on the dynamic context, as a caller with no window to respect has. */
    public ContextInputs(
            final StyleSheet styleSheet,
            @Nullable final String summary,
            final int precedingCount,
            final List<GlossaryEntry> glossary,
            final List<String> earlierMaskedTargets) {
        this(styleSheet, summary, precedingCount, glossary, earlierMaskedTargets, Integer.MAX_VALUE);
    }

    /** Rejects a missing component or a negative count and copies both lists. */
    public ContextInputs {
        Objects.requireNonNull(styleSheet, "styleSheet");
        if (precedingCount < 0) {
            throw new IllegalArgumentException("precedingCount must not be negative: " + precedingCount);
        }
        if (dynamicTokens < 0) {
            throw new IllegalArgumentException("dynamicTokens must not be negative: " + dynamicTokens);
        }
        glossary = List.copyOf(Objects.requireNonNull(glossary, "glossary"));
        earlierMaskedTargets = List.copyOf(Objects.requireNonNull(earlierMaskedTargets, "earlierMaskedTargets"));
        lexicon = List.copyOf(Objects.requireNonNull(lexicon, "lexicon"));
    }
}
