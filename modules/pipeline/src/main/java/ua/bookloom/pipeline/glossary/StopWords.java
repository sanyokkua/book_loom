package ua.bookloom.pipeline.glossary;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * The bundled list, per language, of the words that are often capitalised but never a name — function words,
 * interjections and the like — read from {@code stopwords/<language>.txt} beside this class. A language with no list
 * reads the English one, and nothing is ever fetched.
 *
 * <p>A list is kept small on purpose: a word that is also a common first name ({@code May}, {@code Will}) is left to
 * the lower-case-share rule, which sees how the book itself writes the word.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class StopWords {

    static final String FALLBACK = "en";
    private static final String DIRECTORY = "stopwords/";
    private static final Map<String, Set<String>> LOADED = new ConcurrentHashMap<>();

    /**
     * The stop words of a book's language.
     *
     * @param languageTag the BCP 47 tag of the book's source language, or null when it is not known
     * @return the lower-cased words of that language's list, or of the English list when it has none
     */
    static Set<String> of(@Nullable final String languageTag) {
        final String language = languageTag == null || languageTag.isBlank()
                ? FALLBACK
                : Locale.forLanguageTag(languageTag.strip()).getLanguage();
        final String bundled = StopWords.class.getResource(DIRECTORY + language + ".txt") == null ? FALLBACK : language;
        log.debug("Stop words for language tag {} read from the {} list", languageTag, bundled);
        return LOADED.computeIfAbsent(bundled, StopWords::load);
    }

    private static Set<String> load(final String language) {
        final String name = DIRECTORY + language + ".txt";
        final InputStream stream = StopWords.class.getResourceAsStream(name);
        if (stream == null) {
            throw new IllegalStateException("The bundled stop-word list " + name + " is missing");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            final Set<String> words = reader.lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(Occurrences::keyOf)
                    .collect(Collectors.toUnmodifiableSet());
            log.debug("Stop-word list {} loaded with {} words", name, words.size());
            return words;
        } catch (IOException cause) {
            throw new UncheckedIOException("The bundled stop-word list " + name + " could not be read", cause);
        }
    }
}
