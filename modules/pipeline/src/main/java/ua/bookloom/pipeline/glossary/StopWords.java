package ua.bookloom.pipeline.glossary;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
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
    private static final String ALONE_DIRECTORY = DIRECTORY + "alone/";
    private static final Map<String, Set<String>> LOADED = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> ALONE = new ConcurrentHashMap<>();

    /**
     * The stop words of a book's language.
     *
     * @param languageTag the BCP 47 tag of the book's source language, or null when it is not known
     * @return the lower-cased words of that language's list, or of the English list when it has none
     */
    static Set<String> of(@Nullable final String languageTag) {
        final String bundled = bundled(DIRECTORY, languageOf(languageTag));
        log.debug("Stop words for language tag {} read from the {} list", languageTag, bundled);
        return LOADED.computeIfAbsent(bundled, language -> load(DIRECTORY, language));
    }

    /**
     * The words of a book's language that are never a name on their own, though one may open a longer name: the
     * spelled-out numbers of the bundled {@code stopwords/alone/<language>.txt} list (English when the language has
     * none) and every language's name as the JDK writes it in the book's language — {@code Latin}, {@code French}.
     *
     * @param languageTag the BCP 47 tag of the book's source language, or null when it is not known
     * @return the lower-cased words; never null
     */
    static Set<String> neverAlone(@Nullable final String languageTag) {
        final String language = languageOf(languageTag);
        return ALONE.computeIfAbsent(language, StopWords::loadAlone);
    }

    private static Set<String> loadAlone(final String language) {
        final Locale in = Locale.of(language);
        final Set<String> words = new HashSet<>(load(ALONE_DIRECTORY, bundled(ALONE_DIRECTORY, language)));
        Arrays.stream(Locale.getISOLanguages())
                .map(code -> Locale.of(code).getDisplayLanguage(in))
                .filter(name -> !name.isBlank())
                .map(Occurrences::keyOf)
                .forEach(words::add);
        log.debug("Never-alone words for language {}: {}", language, words.size());
        return Set.copyOf(words);
    }

    private static String languageOf(@Nullable final String languageTag) {
        return languageTag == null || languageTag.isBlank()
                ? FALLBACK
                : Locale.forLanguageTag(languageTag.strip()).getLanguage();
    }

    private static String bundled(final String directory, final String language) {
        return StopWords.class.getResource(directory + language + ".txt") == null ? FALLBACK : language;
    }

    private static Set<String> load(final String directory, final String language) {
        final String name = directory + language + ".txt";
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
