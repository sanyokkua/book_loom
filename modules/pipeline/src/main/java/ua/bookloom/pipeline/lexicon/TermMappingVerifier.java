package ua.bookloom.pipeline.lexicon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.LexiconEntry;

/**
 * Checks the {@code terms} a model reported for one translated item, so only what the text proves is counted. A pair
 * holds when its source term is on the closed key-term list, occurs in the source, and every significant word of its
 * rendering has its stem in the target — an inflected rendering ({@code господаря} for {@code господар}) passes, a
 * word the model never wrote does not. A model that names a rendering it did not use, or a term the item lacks, adds
 * nothing to the lexicon.
 *
 * <p>The stem is a prefix, since the target language is open and no morphology is bundled: a long word may lose its
 * last three letters, a short one fewer, and the target word may be at most three letters longer than the rendering's.
 * For a target language that gives alternation data (15e.9) the last letter of a stem of at least four letters may
 * swap within a group ({@code Прага}, {@code Празькі}), within the same three-letter bound, and a rendering of at least six
 * letters also matches a longer derived form ({@code Лондон}, {@code лондонського}).
 * A rendering whose stem changes inside the word ({@code кінь}, {@code коня}) is dropped, which only costs a count.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TermMappingVerifier {

    /** A term with the rendering the target was shown to use for it. */
    public record Pair(String term, String rendering) {

        /** Rejects missing text. */
        public Pair {
            Objects.requireNonNull(term, "term");
            Objects.requireNonNull(rendering, "rendering");
        }
    }

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}\\p{N}]+(?:['’ʼ-][\\p{L}\\p{M}\\p{N}]+)*");
    private static final int MIN_SIGNIFICANT_LETTERS = 3;
    private static final int LONG_WORD = 8;
    private static final int MEDIUM_WORD = 6;
    private static final int SHORT_WORD = 4;
    private static final int LONG_CUT = 3;
    private static final int MEDIUM_CUT = 2;
    private static final int STEM_FLOOR = 3;
    private static final int SWAP_STEM_FLOOR = 4;
    private static final int DERIVED_WORD_FLOOR = 6;
    private static final int MAX_EXTRA_LETTERS = 3;
    private static final int MAX_RENDERING_WORDS = 5;

    /**
     * Keeps the pairs a text proves.
     *
     * @param claimed the non-null terms the model reported for the item, each with the rendering it says it wrote
     * @param keyTerms the non-null closed list the model was asked about
     * @param source the non-null item's source text, tokens removed
     * @param target the non-null item's translated text, tokens removed
     * @return the verified pairs, each named by the spelling of the key-term list; never null, empty when none holds
     */
    public static List<Pair> verify(
            final Map<String, String> claimed, final List<String> keyTerms, final String source, final String target) {
        return verify(claimed, keyTerms, source, target, List.of());
    }

    /**
     * Keeps the pairs a text proves, for a target language whose names decline and derive adjectives.
     *
     * @param claimed the non-null terms the model reported for the item, each with the rendering it says it wrote
     * @param keyTerms the non-null closed list the model was asked about
     * @param source the non-null item's source text, tokens removed
     * @param target the non-null item's translated text, tokens removed
     * @param alternations the non-null groups of letters a stem's last letter swaps among before a suffix (such as
     *     {@code гзж}); empty for a language with no data, which keeps the plain prefix rule
     * @return the verified pairs, each named by the spelling of the key-term list; never null, empty when none holds
     */
    public static List<Pair> verify(
            final Map<String, String> claimed,
            final List<String> keyTerms,
            final String source,
            final String target,
            final List<String> alternations) {
        Objects.requireNonNull(claimed, "claimed");
        Objects.requireNonNull(alternations, "alternations");
        Objects.requireNonNull(keyTerms, "keyTerms");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        final List<String> targetWords = wordsOf(target, true);
        final List<Pair> verified = new ArrayList<>();
        for (final Map.Entry<String, String> claim : claimed.entrySet()) {
            listed(keyTerms, claim.getKey())
                    .filter(term -> TermMatch.occursIn(term, source))
                    .filter(term -> renderingHolds(claim.getValue(), targetWords, alternations))
                    .ifPresentOrElse(
                            term -> verified.add(new Pair(term, claim.getValue().strip())),
                            () -> log.debug("Term pair dropped: not proven by the item"));
            if (log.isTraceEnabled()) {
                log.trace("Term claim {} -> {}", claim.getKey(), claim.getValue());
            }
        }
        log.debug("Term pairs verified {} of {}", verified.size(), claimed.size());
        return verified;
    }

    private static Optional<String> listed(final List<String> keyTerms, final String claimedTerm) {
        final String key = LexiconEntry.keyOf(claimedTerm);
        return keyTerms.stream()
                .filter(term -> LexiconEntry.keyOf(term).equals(key))
                .findFirst();
    }

    private static boolean renderingHolds(
            final String rendering, final List<String> targetWords, final List<String> alternations) {
        final List<String> words = partsOf(rendering);
        if (words.isEmpty() || words.size() > MAX_RENDERING_WORDS) {
            return false;
        }
        final List<String> significant = words.stream()
                .filter(word -> word.codePointCount(0, word.length()) >= MIN_SIGNIFICANT_LETTERS)
                .toList();
        final List<String> required = significant.isEmpty() ? words : significant;
        return required.stream()
                .allMatch(word -> targetWords.stream().anyMatch(held -> sameWord(word, held, alternations)));
    }

    private static boolean sameWord(
            final String renderingWord, final String targetWord, final List<String> alternations) {
        if (renderingWord.equals(targetWord)) {
            return true;
        }
        final int length = renderingWord.length();
        final String stem = renderingWord.substring(0, stemLength(length));
        if (targetWord.startsWith(stem) && targetWord.length() <= length + MAX_EXTRA_LETTERS) {
            return true;
        }
        if (alternations.isEmpty()
                || stem.length() < SWAP_STEM_FLOOR
                || swappedStems(stem, alternations).noneMatch(targetWord::startsWith)) {
            return false;
        }
        // A short name matches any longer word (Остап, остаточно): only a name of six letters or more is trusted past
        // the plain bound.
        return length >= DERIVED_WORD_FLOOR || targetWord.length() <= length + MAX_EXTRA_LETTERS;
    }

    // A name's adjective or oblique form (лондонського, Празькі for Прага) keeps the stem but may swap its last letter.
    private static Stream<String> swappedStems(final String stem, final List<String> alternations) {
        final String head = stem.substring(0, stem.length() - 1);
        final char last = stem.charAt(stem.length() - 1);
        final Stream<String> swapped = alternations.stream()
                .filter(group -> group.indexOf(last) >= 0)
                .flatMap(group -> group.chars().mapToObj(letter -> head + (char) letter));
        return Stream.concat(Stream.of(stem), swapped);
    }

    private static int stemLength(final int length) {
        final int cut =
                length >= LONG_WORD ? LONG_CUT : length >= MEDIUM_WORD ? MEDIUM_CUT : length >= SHORT_WORD ? 1 : 0;
        return Math.max(Math.min(STEM_FLOOR, length), length - cut);
    }

    // A hyphenated target word counts whole and by each part (Б-Бартімей holds Бартімей); a rendering stays whole.
    private static List<String> wordsOf(final String text, final boolean splitHyphens) {
        return WORD.matcher(text.toLowerCase(Locale.ROOT))
                .results()
                .flatMap(match -> splitHyphens ? withParts(match.group()) : Stream.of(match.group()))
                .toList();
    }

    // A hyphenated rendering is held part by part, because each part declines on its own: Джентльмен-Лузер stands in
    // the
    // target as Джентльмені-Лузері.
    private static List<String> partsOf(final String rendering) {
        return wordsOf(rendering, false).stream()
                .flatMap(word -> Stream.of(word.split("-")))
                .filter(part -> !part.isEmpty())
                .toList();
    }

    private static Stream<String> withParts(final String word) {
        return word.indexOf('-') < 0 ? Stream.of(word) : Stream.concat(Stream.of(word), Stream.of(word.split("-")));
    }
}
