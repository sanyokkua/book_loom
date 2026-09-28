package ua.bookloom.pipeline.prompt;

import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.ForeignPassagePolicy;

/**
 * The four system-message values every model call of a run carries — its language pair, its derived style sheet and
 * its foreign-passage policy — built once per run instead of every call re-deriving the same slots.
 *
 * @param sourceLanguage the project's source language tag, or null when the book declares none
 * @param targetLanguage the project's target language tag
 * @param styleSheet the run's derived style sheet
 * @param foreignPassagePolicy the Book Brief's foreign-passage policy
 */
public record CallFrame(
        @Nullable String sourceLanguage,
        String targetLanguage,
        StyleSheet styleSheet,
        ForeignPassagePolicy foreignPassagePolicy) {

    /** Validates the invariants a caller is entitled to assume. */
    public CallFrame {
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        Objects.requireNonNull(styleSheet, "styleSheet");
        Objects.requireNonNull(foreignPassagePolicy, "foreignPassagePolicy");
    }

    /**
     * Returns this frame's four system-slot values — {@code sourceLanguage}, {@code targetLanguage},
     * {@code styleSheet} and {@code foreignPassageRule} — ready to render any template that declares them.
     *
     * @return the four values, keyed by slot name
     */
    public Map<String, String> systemSlotValues() {
        final String source = PromptLanguages.describe(sourceLanguage);
        final String target = PromptLanguages.describe(targetLanguage);
        return Map.of(
                "sourceLanguage",
                source,
                "targetLanguage",
                target,
                "styleSheet",
                styleSheet.text(),
                "foreignPassageRule",
                StyleSheet.foreignPassageRule(foreignPassagePolicy, source));
    }
}
