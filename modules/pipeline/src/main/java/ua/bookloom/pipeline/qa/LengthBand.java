package ua.bookloom.pipeline.qa;

import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import ua.bookloom.util.lang.Languages;
import ua.bookloom.util.lang.Script;

/**
 * The expected ratio of translated to source length for a language pair.
 *
 * @param lower the smallest expected target/source length ratio
 * @param upper the largest expected target/source length ratio
 */
public record LengthBand(double lower, double upper) {

    private static final Set<Script> CJK = Set.of(Script.HAN, Script.JAPANESE, Script.HANGUL);
    private static final int SHORT_SOURCE_CHARS = 25;
    private static final int MEDIUM_SOURCE_CHARS = 60;
    private static final double MEDIUM_LOWER_FACTOR = 0.85;
    private static final double MEDIUM_UPPER_FACTOR = 1.25;
    private static final LengthBand LATIN_TO_CYRILLIC = new LengthBand(0.7, 1.8);
    private static final LengthBand SAME_SCRIPT = new LengthBand(0.6, 1.7);
    private static final LengthBand LATIN_TO_CJK = new LengthBand(0.2, 1.0);
    private static final LengthBand CJK_TO_LATIN = new LengthBand(1.0, 5.0);
    private static final LengthBand OTHER = new LengthBand(0.5, 2.5);

    /**
     * Selects the band by the two languages' scripts.
     *
     * @param sourceTag the source language tag, or null when unknown
     * @param targetTag the target language tag, or null when unknown
     * @return the pair's unwidened band; the widest default when either language is unrecognized
     */
    public static LengthBand forPair(@Nullable final String sourceTag, @Nullable final String targetTag) {
        final Optional<Script> source = Languages.scriptOf(sourceTag);
        final Optional<Script> target = Languages.scriptOf(targetTag);
        if (source.isEmpty() || target.isEmpty()) {
            return OTHER;
        }
        return classify(source.get(), target.get());
    }

    private static LengthBand classify(final Script source, final Script target) {
        if (source == target) {
            return SAME_SCRIPT;
        }
        if (source == Script.LATIN && target == Script.CYRILLIC) {
            return LATIN_TO_CYRILLIC;
        }
        if (source == Script.LATIN && CJK.contains(target)) {
            return LATIN_TO_CJK;
        }
        return CJK.contains(source) && target == Script.LATIN ? CJK_TO_LATIN : OTHER;
    }

    /**
     * Widens the band for a short source, whose ratio swings widely: a title or a verse line loses its articles and
     * auxiliaries in a language without them and comes out much shorter, though nothing is missing.
     *
     * @param sourceChars the source's display-text length in characters
     * @return this band; with the lower bound halved and the upper doubled when {@code sourceChars} is under 25; with
     *     the lower bound at 0.85 times and the upper at 1.25 times when it is under 60
     */
    public LengthBand widenedFor(final int sourceChars) {
        if (sourceChars < SHORT_SOURCE_CHARS) {
            return new LengthBand(lower / 2, upper * 2);
        }
        return sourceChars < MEDIUM_SOURCE_CHARS
                ? new LengthBand(lower * MEDIUM_LOWER_FACTOR, upper * MEDIUM_UPPER_FACTOR)
                : this;
    }
}
