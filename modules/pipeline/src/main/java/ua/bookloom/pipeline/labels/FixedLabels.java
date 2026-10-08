package ua.bookloom.pipeline.labels;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * The words a book's navigation and cover use for the same few things in every book — "Cover", "Contents", "About" —
 * resolved from the bundled table of one language pair, so they are right and cost no model call (a model rendered
 * "Cover" as the dative of "lid" and cut "About" to "Про"). A pair with no table has no fixed labels and the model
 * translates as before: languages are open.
 *
 * <p>The table is {@code labels/<source>-<target>.properties}, keys the lower-case label with its spaces escaped.
 */
@Slf4j
public final class FixedLabels {

    private static final String DIRECTORY = "/ua/bookloom/pipeline/labels/";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern EDGE_PUNCTUATION = Pattern.compile("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$");
    private static final FixedLabels EMPTY = new FixedLabels(Map.of(), Locale.ROOT);

    private final Map<String, String> table;
    private final Locale targetLocale;

    private FixedLabels(final Map<String, String> table, final Locale targetLocale) {
        this.table = table;
        this.targetLocale = targetLocale;
    }

    /**
     * Opens the table of a language pair.
     *
     * @param sourceLanguage the source language tag, or null when none is declared
     * @param targetLanguage the target language tag, or null when none is chosen
     * @return the pair's labels; one that resolves nothing when the pair has no table
     */
    public static FixedLabels forPair(@Nullable final String sourceLanguage, @Nullable final String targetLanguage) {
        if (sourceLanguage == null || targetLanguage == null) {
            return EMPTY;
        }
        final String source = base(sourceLanguage);
        final String target = base(targetLanguage);
        final Map<String, String> table = load(source + "-" + target);
        log.debug("fixed labels {}->{}: {} entries", source, target, table.size());
        return table.isEmpty() ? EMPTY : new FixedLabels(table, Locale.forLanguageTag(target));
    }

    /**
     * Resolves a segment's whole text.
     *
     * @param text a navigation label, heading, page title or image description, as the book wrote it
     * @return its fixed rendering, in capitals when the source was, or empty when the text is not a fixed label
     */
    public Optional<String> resolve(final String text) {
        Objects.requireNonNull(text, "text");
        if (table.isEmpty()) {
            return Optional.empty();
        }
        final String stripped = EDGE_PUNCTUATION.matcher(text).replaceAll("");
        final String key = WHITESPACE.matcher(stripped).replaceAll(" ").toLowerCase(Locale.ROOT);
        final String found = table.get(key);
        if (found == null) {
            return Optional.empty();
        }
        final boolean shouted = stripped.length() > 1 && stripped.equals(stripped.toUpperCase(Locale.ROOT));
        log.debug("fixed label '{}' resolved, capitals={}", key, shouted);
        return Optional.of(shouted ? found.toUpperCase(targetLocale) : found);
    }

    private static String base(final String tag) {
        final int dash = tag.indexOf('-');
        return (dash > 0 ? tag.substring(0, dash) : tag).toLowerCase(Locale.ROOT);
    }

    private static Map<String, String> load(final String pair) {
        try (InputStream stream = FixedLabels.class.getResourceAsStream(DIRECTORY + pair + ".properties")) {
            if (stream == null) {
                return Map.of();
            }
            final Properties properties = new Properties();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            final Map<String, String> entries = new HashMap<>();
            properties.forEach((key, value) -> entries.put(key.toString(), value.toString()));
            return Map.copyOf(entries);
        } catch (IOException unreadable) {
            log.warn("fixed labels {} unreadable; the model translates them", pair, unreadable);
            return Map.of();
        }
    }
}
