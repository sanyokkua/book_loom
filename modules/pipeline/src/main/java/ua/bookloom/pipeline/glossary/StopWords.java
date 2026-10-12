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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.index.Occurrences;
import ua.bookloom.util.lang.Languages;

/**
 * The bundled list, per language, of the words that are often capitalised but never a name — function words,
 * interjections and the like — read from {@code stopwords/<language>.txt} beside this class. A language with no list,
 * and a book whose language is not known, read every bundled list together: a function word of any bundled language is
 * then never a name, and no language is assumed. Nothing is ever fetched.
 *
 * <p>A list is kept small on purpose: a word that is also a common first name ({@code May}, {@code Will}) is left to
 * the lower-case-share rule, which sees how the book itself writes the word.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StopWords {

    private static final String DIRECTORY = "stopwords/";
    private static final String ALONE_DIRECTORY = DIRECTORY + "alone/";
    private static final String TITLES_DIRECTORY = "titles/";
    private static final String EVERY_LANGUAGE = "*";
    private static final Map<String, Set<String>> LOADED = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> ALONE = new ConcurrentHashMap<>();

    /**
     * The stop words of a book's language.
     *
     * @param languageTag the BCP 47 tag of the book's source language, or null when it is not known
     * @return the lower-cased words of that language's list; of every bundled list together when the language is
     *     unknown or has none
     */
    public static Set<String> of(@Nullable final String languageTag) {
        return listFor(DIRECTORY, languageTag);
    }

    /**
     * The titles ({@code Captain}, {@code Doctor}) of a book's language that a book writes with a capital only.
     *
     * @param languageTag the BCP 47 tag of the book's source language, or null when it is not known
     * @return the lower-cased titles of that language's list; of every bundled list together when the language is
     *     unknown; empty for a known language with no list
     */
    static Set<String> titles(@Nullable final String languageTag) {
        final Optional<String> language = languageOf(languageTag);
        if (language.isEmpty()) {
            return listFor(TITLES_DIRECTORY, null);
        }
        return hasList(TITLES_DIRECTORY, language.get()) ? listFor(TITLES_DIRECTORY, languageTag) : Set.of();
    }

    /**
     * The words of a book's language that are never a name on their own, though one may open a longer name: the
     * spelled-out numbers of the bundled {@code stopwords/alone/<language>.txt} list (every bundled list together when
     * the language is unknown or has none) and every language's name as the JDK writes it in the book's language —
     * {@code Latin}, {@code French} — in English when the language is not known.
     *
     * @param languageTag the BCP 47 tag of the book's source language, or null when it is not known
     * @return the lower-cased words; never null
     */
    static Set<String> neverAlone(@Nullable final String languageTag) {
        final String language = languageOf(languageTag).orElse("");
        return ALONE.computeIfAbsent(language, StopWords::loadAlone);
    }

    private static Set<String> loadAlone(final String language) {
        final Locale in = language.isEmpty() ? Locale.ENGLISH : Locale.of(language);
        final Set<String> words = new HashSet<>(listFor(ALONE_DIRECTORY, language));
        Arrays.stream(Locale.getISOLanguages())
                .map(code -> Locale.of(code).getDisplayLanguage(in))
                .filter(name -> !name.isBlank())
                .map(Occurrences::keyOf)
                .forEach(words::add);
        log.debug("Never-alone words for language {}: {}", language, words.size());
        return Set.copyOf(words);
    }

    private static Optional<String> languageOf(@Nullable final String languageTag) {
        return languageTag == null || languageTag.isBlank()
                ? Optional.empty()
                : Optional.of(Locale.forLanguageTag(languageTag.strip()).getLanguage());
    }

    private static boolean hasList(final String directory, final String language) {
        return StopWords.class.getResource(directory + language + ".txt") != null;
    }

    private static Set<String> listFor(final String directory, @Nullable final String languageTag) {
        final Optional<String> own = languageOf(languageTag).filter(language -> hasList(directory, language));
        log.debug("List {} for language tag {} read from {}", directory, languageTag, own.orElse("every bundled list"));
        return own.map(language -> LOADED.computeIfAbsent(directory + language, _ -> load(directory, language)))
                .orElseGet(() -> LOADED.computeIfAbsent(directory + EVERY_LANGUAGE, _ -> loadEvery(directory)));
    }

    private static Set<String> loadEvery(final String directory) {
        final Set<String> words = new HashSet<>();
        Languages.all().stream()
                .map(language -> Locale.forLanguageTag(language.tag()).getLanguage())
                .distinct()
                .filter(language -> hasList(directory, language))
                .forEach(language -> words.addAll(load(directory, language)));
        log.debug("Every bundled list of {} holds {} words", directory, words.size());
        return Set.copyOf(words);
    }

    /**
     * The stop words of a language only when a list is bundled for it — unlike {@link #of}, which reads every list for
     * a language with none, a wrong answer for a check that must know the language is really there.
     *
     * @param languageTag the BCP 47 tag of the language, or null when it is not known
     * @return the lower-cased words of that language's own list, or empty when none is bundled
     */
    public static Optional<Set<String>> bundled(@Nullable final String languageTag) {
        return languageOf(languageTag)
                .filter(language -> hasList(DIRECTORY, language))
                .map(language -> LOADED.computeIfAbsent(DIRECTORY + language, _ -> load(DIRECTORY, language)));
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
