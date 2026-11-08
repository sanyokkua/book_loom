package ua.bookloom.pipeline.memory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.checks.FunctionWords;
import ua.bookloom.pipeline.glossary.NameForms;

/**
 * The words a chapter's summary may use, so a word the model made up is told from a real one without a dictionary: the
 * words of the chapter's decided target text, every form of the glossary's target names, the language's bundled
 * function words and the closed list of numerals and story verbs beside {@code LanguageRules}' files — a short
 * list of allowed everyday words for the summary gate alone, no dictionary: nothing in the translation or the
 * glossary reads it. A word counts as
 * known when it is one of those or shares the stem of one (its letters less a two-letter ending, at least four), which
 * lets an inflected form pass. Words of fewer than three letters are never judged.
 */
@Slf4j
final class SummaryVocabulary {

    /** The fewest letters a word has to be judged; shorter ones are particles and prepositions. */
    static final int MIN_CHECKED_LETTERS = 3;

    private static final int MIN_STEM_LETTERS = 4;
    private static final int ENDING_LETTERS = 2;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}]++(?:['’ʼ][\\p{L}\\p{M}]++)*+");
    private static final Pattern APOSTROPHES = Pattern.compile("[’ʼ]");
    private static final String ALLOWED_DIRECTORY = "/ua/bookloom/pipeline/prompt/languages/summary-words/";
    private static final String ALLOWED_SUFFIX = ".txt";

    private final NavigableSet<String> words;
    private final boolean hasDecidedText;

    private SummaryVocabulary(final NavigableSet<String> words, final boolean hasDecidedText) {
        this.words = words;
        this.hasDecidedText = hasDecidedText;
    }

    /**
     * Builds the vocabulary of one chapter.
     *
     * @param decidedLines the non-null decided target text of the chapter's segments, one per segment, in reading order
     * @param entries the non-null glossary entries, whose target spellings are read
     * @param targetLanguage the non-null target language tag; only its primary subtag picks the bundled lists
     * @return the vocabulary; never null. It {@linkplain #isUsable() is not usable} when no decided text was given
     */
    static SummaryVocabulary of(
            final List<String> decidedLines, final List<GlossaryEntry> entries, final String targetLanguage) {
        Objects.requireNonNull(decidedLines, "decidedLines");
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        final NavigableSet<String> words = new TreeSet<>();
        decidedLines.forEach(line -> words.addAll(wordsOf(line)));
        final int decided = words.size();
        entries.forEach(entry -> words.addAll(targetWords(entry, targetLanguage)));
        final String language = Locale.forLanguageTag(targetLanguage.strip()).getLanguage();
        words.addAll(FunctionWords.of(language));
        // Read for each chapter, a few kilobytes once a chapter ends, so no shared state outlives the call.
        words.addAll(allowed(language));
        log.debug(
                "Summary vocabulary language={} lines={} decidedWords={} total={}",
                language,
                decidedLines.size(),
                decided,
                words.size());
        return new SummaryVocabulary(words, decided > 0);
    }

    /**
     * Whether the vocabulary can judge a sentence.
     *
     * @return {@code true} if decided target text gave it words, {@code false} if there was none to compare with
     */
    boolean isUsable() {
        return hasDecidedText;
    }

    /**
     * The words of a sentence the vocabulary does not know.
     *
     * @param sentence the non-null sentence
     * @return the unknown words as written, in order and distinct; never null, empty when all are known
     */
    List<String> unknownIn(final String sentence) {
        final List<String> unknown = new ArrayList<>();
        final Matcher matcher = WORD.matcher(Objects.requireNonNull(sentence, "sentence"));
        while (matcher.find()) {
            final String word = matcher.group();
            if (!unknown.contains(word) && !isKnown(normalize(word))) {
                unknown.add(word);
            }
        }
        return unknown;
    }

    private boolean isKnown(final String word) {
        if (word.codePointCount(0, word.length()) < MIN_CHECKED_LETTERS || words.contains(word)) {
            return true;
        }
        final int length = word.length();
        final String stem = word.substring(
                0, Math.clamp((long) length - ENDING_LETTERS, Math.min(MIN_STEM_LETTERS, length), length));
        final String next = words.ceiling(stem);
        return next != null && next.startsWith(stem);
    }

    private static Set<String> targetWords(final GlossaryEntry entry, final String targetLanguage) {
        final String target = entry.target();
        if (target == null || target.isBlank()) {
            return Set.of();
        }
        return NameForms.forms(target.strip(), entry.gender(), targetLanguage).all().stream()
                .flatMap(form -> wordsOf(form).stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static List<String> wordsOf(final String text) {
        final List<String> found = new ArrayList<>();
        final Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            found.add(normalize(matcher.group()));
        }
        return found;
    }

    private static String normalize(final String word) {
        return APOSTROPHES
                .matcher(Normalizer.normalize(word, Normalizer.Form.NFC).toLowerCase(Locale.ROOT))
                .replaceAll("'");
    }

    private static Set<String> allowed(final String language) {
        final String name = ALLOWED_DIRECTORY + language + ALLOWED_SUFFIX;
        try (InputStream stream = SummaryVocabulary.class.getResourceAsStream(name)) {
            if (stream == null) {
                log.debug("No summary-word file {}", name);
                return Set.of();
            }
            final Set<String> loaded = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(SummaryVocabulary::normalize)
                    .collect(Collectors.toUnmodifiableSet());
            log.debug("Summary-word file {} loaded with {} words", name, loaded.size());
            return loaded;
        } catch (IOException cause) {
            throw new UncheckedIOException("The bundled summary-word file " + name + " could not be read", cause);
        }
    }
}
