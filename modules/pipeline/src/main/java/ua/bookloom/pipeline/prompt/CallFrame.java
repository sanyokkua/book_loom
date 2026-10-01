package ua.bookloom.pipeline.prompt;

import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.project.ForeignPassagePolicy;

/**
 * The four system-message values every model call of a run carries — its language pair, its derived style sheet and
 * its foreign-passage policy — built once per run instead of every call re-deriving the same slots, plus the language
 * the book itself declares, which decides with the policy which blocks are foreign.
 *
 * @param sourceLanguage the project's source language tag, or null when the book declares none
 * @param targetLanguage the project's target language tag
 * @param styleSheet the run's derived style sheet
 * @param foreignPassagePolicy the Book Brief's foreign-passage policy
 * @param bookLanguage the language the book's own metadata declares, or null when it declares none or is not known
 *     here; a block declaring this language is never a foreign passage, even when the brief's source differs
 */
public record CallFrame(
        @Nullable String sourceLanguage,
        String targetLanguage,
        StyleSheet styleSheet,
        ForeignPassagePolicy foreignPassagePolicy,
        @Nullable String bookLanguage) {

    /** Validates the invariants a caller is entitled to assume. */
    public CallFrame {
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        Objects.requireNonNull(styleSheet, "styleSheet");
        Objects.requireNonNull(foreignPassagePolicy, "foreignPassagePolicy");
    }

    /** A frame for a call made without the book at hand, so no block is compared with the book's own language. */
    public CallFrame(
            @Nullable String sourceLanguage,
            String targetLanguage,
            StyleSheet styleSheet,
            ForeignPassagePolicy foreignPassagePolicy) {
        this(sourceLanguage, targetLanguage, styleSheet, foreignPassagePolicy, null);
    }

    /**
     * The language a book declares of itself: its metadata's declaration, else the language most of its content
     * documents declare.
     *
     * @param document the non-null book
     * @return the tag as written, or null when the book declares none
     */
    public static @Nullable String bookLanguageOf(final Document document) {
        Objects.requireNonNull(document, "document");
        return document.declaredLang() != null ? document.declaredLang() : document.detectedSourceLang();
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
