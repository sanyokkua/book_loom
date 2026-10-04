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
import java.util.Properties;
import java.util.stream.Collectors;
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
final class QuoteConventions {

    private static final String RESOURCE = "quote-pairs.properties";
    private static final String FALLBACK_KEY = "*";
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
