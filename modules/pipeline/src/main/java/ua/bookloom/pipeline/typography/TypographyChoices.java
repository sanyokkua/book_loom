package ua.bookloom.pipeline.typography;

import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.QuoteStyle;

/**
 * What the Brief decided about the translated book's apostrophe and quote marks; a null key means the target
 * language's own default. Carried to the typography pass beside the target language.
 *
 * @param apostrophe the Brief's apostrophe (one code point), or null for the language's default
 * @param quoteStyle the Brief's quotation marks, or null for the language's default
 */
public record TypographyChoices(
        @Nullable String apostrophe, @Nullable QuoteStyle quoteStyle) {

    /** Both keys at the language's default. */
    public static final TypographyChoices DEFAULTS = new TypographyChoices(null, null);

    /**
     * The choices a brief holds.
     *
     * @param brief the non-null brief
     * @return its apostrophe and quote style keys
     */
    public static TypographyChoices of(final BookBrief brief) {
        return new TypographyChoices(brief.apostrophe(), brief.quoteStyle());
    }
}
