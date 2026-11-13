package ua.bookloom.pipeline.checks;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.index.Occurrences;

/**
 * The bundled short grammar words (articles, prepositions, conjunctions, pronouns, particles, auxiliaries) of a
 * language, read from {@code function-words/<language>.txt} beside this class. The words a source language has and the
 * target language does not are the ones that betray untranslated text, so no list is written for one language pair:
 * the pair's set is computed. A word both languages use belongs to the target's own file, which is what keeps it out
 * of the pair's set. Nothing is ever fetched.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FunctionWords {

    private static final String DIRECTORY = "function-words/";
    private static final Map<String, Set<String>> LOADED = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> PAIRS = new ConcurrentHashMap<>();

    /**
     * The function words of one language.
     *
     * @param languageTag a BCP 47 tag; only its primary subtag picks the file ({@code uk-UA} reads {@code uk.txt})
     * @return the lower-cased words; empty, with one DEBUG line, when no file is bundled for the language
     */
    public static Set<String> of(final String languageTag) {
        final String language = Locale.forLanguageTag(languageTag.strip()).getLanguage();
        return LOADED.computeIfAbsent(language, FunctionWords::load);
    }

    /**
     * The words the source language has and the target language does not.
     *
     * @param sourceTag a BCP 47 tag of the book's language
     * @param targetTag a BCP 47 tag of the translation's language
     * @return the source's set minus the target's, cached per pair; empty when either file is missing, which turns
     *     the check off for that pair
     */
    public static Set<String> sourceOnly(final String sourceTag, final String targetTag) {
        final String key = Locale.forLanguageTag(sourceTag.strip()).getLanguage()
                + ">"
                + Locale.forLanguageTag(targetTag.strip()).getLanguage();
        return PAIRS.computeIfAbsent(key, _ -> {
            final Set<String> source = of(sourceTag);
            final Set<String> target = of(targetTag);
            if (source.isEmpty() || target.isEmpty()) {
                log.debug("Function words {}: a file is missing, the pair has no source-only words", key);
                return Set.of();
            }
            final Set<String> only = difference(source, target);
            log.debug("Function words {}: {} source-only words", key, only.size());
            return only;
        });
    }

    /** The words of the source set that the target set lacks. */
    static Set<String> difference(final Set<String> source, final Set<String> target) {
        final Set<String> only = new HashSet<>(source);
        only.removeAll(target);
        return Set.copyOf(only);
    }

    /**
     * Reads a function-word file's text.
     *
     * @param text one word per line; lines starting with {@code #} and blank lines are ignored
     * @return the lower-cased words
     */
    static Set<String> parse(final String text) {
        return text.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .map(Occurrences::keyOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> load(final String language) {
        final String name = DIRECTORY + language + ".txt";
        try (InputStream stream = FunctionWords.class.getResourceAsStream(name)) {
            if (stream == null) {
                log.debug("No function-word file {}", name);
                return Set.of();
            }
            final Set<String> words = parse(new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n")));
            log.debug("Function-word file {} loaded with {} words", name, words.size());
            return words;
        } catch (IOException cause) {
            throw new UncheckedIOException("The bundled function-word file " + name + " could not be read", cause);
        }
    }
}
