package ua.bookloom.pipeline.lexicon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
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
        Objects.requireNonNull(claimed, "claimed");
        Objects.requireNonNull(keyTerms, "keyTerms");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        final List<String> targetWords = wordsOf(target);
        final List<Pair> verified = new ArrayList<>();
        for (final Map.Entry<String, String> claim : claimed.entrySet()) {
            listed(keyTerms, claim.getKey())
                    .filter(term -> TermMatch.occursIn(term, source))
                    .filter(term -> renderingHolds(claim.getValue(), targetWords))
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

    private static boolean renderingHolds(final String rendering, final List<String> targetWords) {
        final List<String> words = wordsOf(rendering);
        if (words.isEmpty() || words.size() > MAX_RENDERING_WORDS) {
            return false;
        }
        final List<String> significant = words.stream()
                .filter(word -> word.codePointCount(0, word.length()) >= MIN_SIGNIFICANT_LETTERS)
                .toList();
        final List<String> required = significant.isEmpty() ? words : significant;
        return required.stream().allMatch(word -> targetWords.stream().anyMatch(held -> sameWord(word, held)));
    }

    private static boolean sameWord(final String renderingWord, final String targetWord) {
        if (renderingWord.equals(targetWord)) {
            return true;
        }
        final int length = renderingWord.length();
        return targetWord.startsWith(renderingWord.substring(0, stemLength(length)))
                && targetWord.length() <= length + MAX_EXTRA_LETTERS;
    }

    private static int stemLength(final int length) {
        final int cut =
                length >= LONG_WORD ? LONG_CUT : length >= MEDIUM_WORD ? MEDIUM_CUT : length >= SHORT_WORD ? 1 : 0;
        return Math.max(Math.min(STEM_FLOOR, length), length - cut);
    }

    private static List<String> wordsOf(final String text) {
        return WORD.matcher(text.toLowerCase(Locale.ROOT))
                .results()
                .map(match -> match.group())
                .toList();
    }
}
