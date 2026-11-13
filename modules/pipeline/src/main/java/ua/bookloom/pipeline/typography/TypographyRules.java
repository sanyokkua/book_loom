package ua.bookloom.pipeline.typography;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import ua.bookloom.api.project.QuoteStyle;
import ua.bookloom.pipeline.checks.QuoteConventions;
import ua.bookloom.pipeline.checks.QuotePair;

/**
 * What the typography pass does for one language: the apostrophe it writes, the marks that take no space before them,
 * and the quote pairs when the language has a convention of its own. A resource, so a language is added without
 * touching the pass.
 *
 * @param apostrophe the character that replaces a straight apostrophe inside a word
 * @param noSpaceBefore the marks that never follow a space
 * @param quotes the language's quote pairs, primary first and nested second; empty when its quotes are not rewritten
 * @param isCommaOutsideQuote whether a comma belongs after a closing quote that is followed by a dash
 * @param isSpeechRepaired whether the source-aware speech rewrites apply to the language
 * @param isDialogueDashWritten whether the language writes speech with a leading dash, which turns on the lost-dash
 *     and one-form dash repairs
 */
record TypographyRules(
        char apostrophe,
        String noSpaceBefore,
        List<QuotePair> quotes,
        boolean isCommaOutsideQuote,
        boolean isSpeechRepaired,
        boolean isDialogueDashWritten) {

    private static final String RESOURCE = "typography-rules.properties";
    private static final String FALLBACK_KEY = "*";
    private static final Properties LINES = load();

    static TypographyRules forLanguage(final String languageTag) {
        Objects.requireNonNull(languageTag, "languageTag");
        final String language = Locale.forLanguageTag(languageTag).getLanguage();
        return new TypographyRules(
                property(language, "apostrophe").charAt(0),
                property(language, "no-space-before"),
                QuoteConventions.ownLine(languageTag).orElse(List.of()),
                Boolean.parseBoolean(property(language, "comma-outside-quote")),
                Boolean.parseBoolean(property(language, "repair-speech")),
                Boolean.parseBoolean(property(language, "dialogue-dash")));
    }

    /**
     * These rules with the Brief's keys applied: its apostrophe when it is a single character, its quote pairs when
     * all four marks are single characters; anything else keeps the language's own.
     */
    TypographyRules with(final TypographyChoices choices) {
        final String mark = choices.apostrophe();
        final QuoteStyle style = choices.quoteStyle();
        return new TypographyRules(
                mark != null && mark.length() == 1 ? mark.charAt(0) : apostrophe,
                noSpaceBefore,
                style != null && isSingleChars(style) ? pairsOf(style) : quotes,
                isCommaOutsideQuote,
                isSpeechRepaired,
                isDialogueDashWritten);
    }

    private static boolean isSingleChars(final QuoteStyle style) {
        return style.outerOpen().length() == 1
                && style.outerClose().length() == 1
                && style.innerOpen().length() == 1
                && style.innerClose().length() == 1;
    }

    private static List<QuotePair> pairsOf(final QuoteStyle style) {
        return List.of(
                new QuotePair(style.outerOpen().charAt(0), style.outerClose().charAt(0)),
                new QuotePair(style.innerOpen().charAt(0), style.innerClose().charAt(0)));
    }

    private static String property(final String language, final String name) {
        return Optional.ofNullable(LINES.getProperty(language + "." + name))
                .orElseGet(() -> Objects.requireNonNull(LINES.getProperty(FALLBACK_KEY + "." + name)));
    }

    private static Properties load() {
        final Properties lines = new Properties();
        try (InputStream in = TypographyRules.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("The bundled typography table " + RESOURCE + " is missing");
            }
            lines.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException cause) {
            throw new UncheckedIOException("The bundled typography table could not be read", cause);
        }
        return lines;
    }
}
