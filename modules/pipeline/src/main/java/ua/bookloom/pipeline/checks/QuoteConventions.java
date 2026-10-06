package ua.bookloom.pipeline.checks;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * The per-language table of quote pairs, read from {@code quote-pairs.properties} beside this class. It is a
 * resource, not code, so a language's convention is added without touching a check — the later language-rules map
 * absorbs it.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class QuoteConventions {

    private static final String RESOURCE = "quote-pairs.properties";
    private static final String FALLBACK_KEY = "*";
    private static final QuotePair ENGLISH = new QuotePair('“', '”');
    private static final Map<String, List<QuotePair>> TABLE = load();

    /**
     * The quote pairs of a language.
     *
     * @param languageTag a BCP 47 tag, or null when the language is not known
     * @return the pairs of that language's line, or of the general line when it has none; never empty
     */
    static List<QuotePair> forLanguage(@Nullable final String languageTag) {
        final String language = languageTag == null
                ? FALLBACK_KEY
                : Locale.forLanguageTag(languageTag).getLanguage();
        final List<QuotePair> pairs =
                Objects.requireNonNull(TABLE.containsKey(language) ? TABLE.get(language) : TABLE.get(FALLBACK_KEY));
        return pairs;
    }

    /**
     * A language's pairs plus English curly quotes, which a model often writes whatever the target language: the typography
     * pass leaves typographic marks alone, so a balanced “…” run is a style note, not a defect.
     *
     * @param pairs a language's own pairs
     * @return {@code pairs}, with the “ ” pair added when none of them opens with “
     */
    static List<QuotePair> withEnglish(final List<QuotePair> pairs) {
        if (pairs.stream().anyMatch(pair -> pair.open() == ENGLISH.open())) {
            return pairs;
        }
        return Stream.concat(pairs.stream(), Stream.of(ENGLISH)).toList();
    }

    /**
     * The quote pairs of a language that has a line of its own, so a caller that rewrites quotes touches only the
     * languages whose convention is known.
     *
     * @param languageTag a BCP 47 tag
     * @return the language's pairs, primary first and nested second where it has one; empty when only the general line
     *     applies
     */
    public static Optional<List<QuotePair>> ownLine(final String languageTag) {
        Objects.requireNonNull(languageTag, "languageTag");
        return Optional.ofNullable(TABLE.get(Locale.forLanguageTag(languageTag).getLanguage()));
    }

    /**
     * Whether a text's quote marks pair up under a language's table, with the same rule the quote-balance check uses.
     *
     * @param text any text; tokens and unlisted marks are ignored
     * @param languageTag a BCP 47 tag, or null when the language is not known
     * @return {@code true} when every opening mark is closed by its own mark and none is closed unopened
     */
    public static boolean isBalanced(final String text, @Nullable final String languageTag) {
        Objects.requireNonNull(text, "text");
        return QuoteBalanceCheck.isBalanced(text, forLanguage(languageTag));
    }

    private static Map<String, List<QuotePair>> load() {
        final Properties lines = new Properties();
        try (InputStream in = QuoteConventions.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("The bundled quote table " + RESOURCE + " is missing");
            }
            lines.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException cause) {
            throw new UncheckedIOException("The bundled quote table could not be read", cause);
        }
        return lines.stringPropertyNames().stream()
                .collect(Collectors.toUnmodifiableMap(key -> key, key -> pairsOf(lines.getProperty(key))));
    }

    private static List<QuotePair> pairsOf(final String line) {
        return Arrays.stream(line.strip().split("\\s+"))
                .map(pair -> new QuotePair(pair.charAt(0), pair.charAt(1)))
                .toList();
    }
}
