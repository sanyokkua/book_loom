package ua.bookloom.pipeline.chunk;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.qa.LengthBand;
import ua.bookloom.util.lang.LanguageTags;
import ua.bookloom.util.lang.Languages;
import ua.bookloom.util.lang.Script;

/** Character-based token estimates: cheap, deterministic and good enough to size chunks and timeouts. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TokenEstimator {

    private static final double SAFETY_FACTOR = 1.15;
    private static final double ROUNDING_TOLERANCE = 1e-9;

    /**
     * Estimates the tokens of a text.
     *
     * @param text the text; never null
     * @param languageTag the text's language tag, or null or unrecognized for the 3.0 characters-per-token figure
     * @return the estimated token count; 0 for an empty text
     */
    public static int estimate(final String text, @Nullable final String languageTag) {
        return ceil(text.codePointCount(0, text.length()) / charsPerToken(languageTag) * SAFETY_FACTOR);
    }

    /**
     * Estimates how many tokens the translation of a source may take, from the widest length the pair allows.
     *
     * @param sourceDisplayText the source's display text; never null
     * @param sourceTag the source language tag, or null when unknown
     * @param targetTag the target language tag; never null
     * @return the expected output tokens; 0 for an empty source
     */
    public static int outputAllowance(
            final String sourceDisplayText, @Nullable final String sourceTag, final String targetTag) {
        final double upper = LengthBand.forPair(sourceTag, targetTag).upper();
        final int chars = sourceDisplayText.codePointCount(0, sourceDisplayText.length());
        return ceil(chars * upper / charsPerToken(targetTag) * SAFETY_FACTOR);
    }

    private static int ceil(final double value) {
        return (int) Math.ceil(value - ROUNDING_TOLERANCE);
    }

    static double charsPerToken(@Nullable final String tag) {
        return LanguageTags.normalize(tag)
                .flatMap(Languages::byTag)
                .map(language -> language.script())
                .orElse(Script.UNKNOWN)
                .charsPerToken();
    }
}
