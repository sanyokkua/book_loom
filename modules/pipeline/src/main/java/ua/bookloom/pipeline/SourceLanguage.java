package ua.bookloom.pipeline;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.util.lang.LanguageTags;

/**
 * The one answer to "what is the source language of this run". A brief with no source language is Auto: the model
 * identifies the language, so a prompt must never be told the book's own declaration, while the code's own analysis
 * (name scans, pronouns, stop words) may still use a declaration it recognises and otherwise runs on generic rules.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SourceLanguage {

    /**
     * The language a prompt names.
     *
     * @param brief the Book Brief; never null
     * @return the tag the person chose, or {@code null} for Auto, which a prompt renders as "infer it from its text"
     */
    public static @Nullable String forPrompt(final BookBrief brief) {
        Objects.requireNonNull(brief, "brief");
        return brief.sourceLanguage();
    }

    /**
     * The language the code's own analysis reads the book in.
     *
     * @param brief the Book Brief; never null
     * @param document the opened book, whose declaration serves only under Auto; never null
     * @return the tag the person chose, else the book's declared tag when the application recognises it (in its normal
     *     form), else {@code null}: no analysis language, so the generic rules apply
     */
    public static @Nullable String forAnalysis(final BookBrief brief, final Document document) {
        Objects.requireNonNull(brief, "brief");
        return forAnalysis(brief.sourceLanguage(), document);
    }

    /**
     * The analysis language for a source language read straight from a stored brief.
     *
     * @param chosen the tag the person chose, or {@code null} for Auto
     * @param document the opened book, whose declaration serves only under Auto; never null
     * @return as {@link #forAnalysis(BookBrief, Document)}
     */
    public static @Nullable String forAnalysis(final @Nullable String chosen, final Document document) {
        Objects.requireNonNull(document, "document");
        if (chosen != null) {
            return chosen;
        }
        final String analysis = LanguageTags.normalize(document.declaredLang()).orElse(null);
        log.debug("Auto source: declared={} analysis={}", document.declaredLang(), analysis);
        return analysis;
    }
}
