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
 */
record TypographyRules(char apostrophe, String noSpaceBefore, List<QuotePair> quotes) {

    private static final String RESOURCE = "typography-rules.properties";
    private static final String FALLBACK_KEY = "*";
    private static final Properties LINES = load();

    static TypographyRules forLanguage(final String languageTag) {
        Objects.requireNonNull(languageTag, "languageTag");
        final String language = Locale.forLanguageTag(languageTag).getLanguage();
        return new TypographyRules(
                property(language, "apostrophe").charAt(0),
                property(language, "no-space-before"),
                QuoteConventions.ownLine(languageTag).orElse(List.of()));
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
