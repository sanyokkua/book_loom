package ua.bookloom.pipeline.chunk;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.qa.LengthBand;
import ua.bookloom.util.lang.Languages;
import ua.bookloom.util.lang.Script;

/** Character-based token estimates: cheap, deterministic and good enough to size chunks and timeouts. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TokenEstimator {

    private static final double SAFETY_FACTOR = 1.15;
    private static final double ROUNDING_TOLERANCE = 1e-9;
    private static final int CAP_FLOOR = 64;
    private static final int CAP_ALLOWANCE_NUMERATOR = 3;
    private static final int CAP_ALLOWANCE_DENOMINATOR = 2;
    private static final int CAP_FIXED_HEADROOM = 16;
    private static final int CAP_TOKENS_PER_PLACEHOLDER = 6;

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

    /**
     * The hard cap on a reply that is expected to take {@code allowance} tokens: half as much again, a fixed
     * headroom and room for each placeholder token, never below a floor that keeps a short reply from being cut.
     *
     * @param allowance the expected output tokens from {@link #outputAllowance}; not negative
     * @param placeholderTokens how many {@code ⟦gN⟧} tokens the source holds; not negative
     * @return the cap in tokens; at least 64
     */
    public static int outputCap(final int allowance, final int placeholderTokens) {
        final int scaled = Math.ceilDiv(allowance * CAP_ALLOWANCE_NUMERATOR, CAP_ALLOWANCE_DENOMINATOR);
        return Math.max(CAP_FLOOR, scaled + CAP_FIXED_HEADROOM + CAP_TOKENS_PER_PLACEHOLDER * placeholderTokens);
    }

    private static int ceil(final double value) {
        return (int) Math.ceil(value - ROUNDING_TOLERANCE);
    }

    static double charsPerToken(@Nullable final String tag) {
        return Languages.scriptOf(tag).orElse(Script.UNKNOWN).charsPerToken();
    }
}
