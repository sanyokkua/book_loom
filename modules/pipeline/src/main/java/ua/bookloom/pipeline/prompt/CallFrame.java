package ua.bookloom.pipeline.prompt;

import java.lang.Character.UnicodeScript;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.pipeline.SourceLanguage;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.index.BookIndex;

/**
 * What every model call of a run carries: its language pair, derived style sheet and foreign-passage policy, built once
 * per run instead of every call re-deriving the same slots, plus the book's own language and script, which decide with
 * the policy which blocks are foreign. Two source languages live here on purpose: {@code sourceLanguage} is what a
 * prompt may name (the person's choice, or none under Auto), while {@code analysisLanguage} is what the code's own
 * readers (name scans, pronoun evidence, stop words) may use — under Auto a declared tag the application recognises.
 *
 * @param sourceLanguage the source language tag the person chose, or null for Auto; the only source a prompt names
 * @param targetLanguage the project's target language tag
 * @param styleSheet the run's derived style sheet
 * @param foreignPassagePolicy the Book Brief's foreign-passage policy
 * @param bookLanguage the language the book's own metadata declares, or null when it declares none or is not known
 *     here; a block declaring this language is never a foreign passage, even when the brief's source differs
 * @param narrator the Book Brief's narrator, read by the gender check; the style sheet already tells the model
 * @param wordValidator the run's word validator, which the quality checks ask about doubtful words; the no-op one
 *     unless a run is given another
 * @param bookScript the script most of the book's story is written in, counted by code; with no source language it
 *     decides which marked runs are foreign. Null when unknown, then the segment's own text decides
 * @param analysisLanguage the language the code reads the book in, from {@link SourceLanguage#forAnalysis}: the chosen
 *     tag, else a recognised declared tag, else null for the generic rules; never put into a prompt
 */
public record CallFrame(
        @Nullable String sourceLanguage,
        String targetLanguage,
        StyleSheet styleSheet,
        ForeignPassagePolicy foreignPassagePolicy,
        @Nullable String bookLanguage,
        Narrator narrator,
        WordValidator wordValidator,
        @Nullable UnicodeScript bookScript,
        @Nullable String analysisLanguage) {

    /** Validates the invariants a caller is entitled to assume. */
    public CallFrame {
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        Objects.requireNonNull(styleSheet, "styleSheet");
        Objects.requireNonNull(foreignPassagePolicy, "foreignPassagePolicy");
        Objects.requireNonNull(narrator, "narrator");
        Objects.requireNonNull(wordValidator, "wordValidator");
    }

    /**
     * A frame whose run has no word validator, book script or opened book; the analysis language is the chosen source,
     * as no book is at hand to read a declaration from.
     */
    public CallFrame(
            @Nullable String sourceLanguage,
            String targetLanguage,
            StyleSheet styleSheet,
            ForeignPassagePolicy foreignPassagePolicy,
            @Nullable String bookLanguage,
            Narrator narrator) {
        this(
                sourceLanguage,
                targetLanguage,
                styleSheet,
                foreignPassagePolicy,
                bookLanguage,
                narrator,
                WordValidator.none(),
                null,
                sourceLanguage);
    }

    /** A frame for a book whose narrator is not stated. */
    public CallFrame(
            @Nullable String sourceLanguage,
            String targetLanguage,
            StyleSheet styleSheet,
            ForeignPassagePolicy foreignPassagePolicy,
            @Nullable String bookLanguage) {
        this(sourceLanguage, targetLanguage, styleSheet, foreignPassagePolicy, bookLanguage, Narrator.unspecified());
    }

    /** A frame for a call made without the book at hand, so no block is compared with the book's own language. */
    public CallFrame(
            @Nullable String sourceLanguage,
            String targetLanguage,
            StyleSheet styleSheet,
            ForeignPassagePolicy foreignPassagePolicy) {
        this(sourceLanguage, targetLanguage, styleSheet, foreignPassagePolicy, null, Narrator.unspecified());
    }

    /**
     * The frame of one run: the brief's languages, policy and narrator, the book's own language and script.
     *
     * @param brief the run's brief, whose target language the run start has already checked
     * @param styleSheet the run's derived style sheet
     * @param document the book being translated
     * @param bookScript the script most of the story is written in, or null when unknown
     * @param words the run's word validator
     * @return the frame every call of the run carries
     */
    public static CallFrame forRun(
            final BookBrief brief,
            final StyleSheet styleSheet,
            final Document document,
            final @Nullable UnicodeScript bookScript,
            final WordValidator words) {
        Objects.requireNonNull(brief, "brief");
        Objects.requireNonNull(document, "document");
        return new CallFrame(
                SourceLanguage.forPrompt(brief),
                Objects.requireNonNull(brief.targetLanguage(), "target language checked at the start"),
                styleSheet,
                brief.foreignPassages(),
                bookLanguageOf(document),
                brief.narrator(),
                words,
                bookScript,
                SourceLanguage.forAnalysis(brief, document));
    }

    /**
     * The frame of a call made on an opened book outside a translation run — the setup assistant, a glossary scan, the
     * consistency pass — with the brief's own style sheet and no word validator.
     *
     * @param brief the project's brief, whose target language the caller has already checked
     * @param document the opened book
     * @param index the book's index, or null when it is not built; it only supplies the book's script
     * @return the frame the call carries
     */
    public static CallFrame forBook(final BookBrief brief, final Document document, final @Nullable BookIndex index) {
        return forRun(brief, StyleSheet.from(brief), document, scriptOf(index), WordValidator.none());
    }

    /**
     * The script most of an indexed book's story is written in.
     *
     * @param index the book's index, or null when it is not built
     * @return the majority script, or null when there is no index or no script holds a majority
     */
    public static @Nullable UnicodeScript scriptOf(final @Nullable BookIndex index) {
        return index == null ? null : index.majorityScript().orElse(null);
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
