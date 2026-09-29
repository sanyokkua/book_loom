package ua.bookloom.pipeline.prompt;

import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.chunk.TokenEstimator;

/**
 * What a call that states an expected output tells the provider about its reply length.
 *
 * @param expectedTokens the expected completion length, which scales the request timeout; positive
 * @param capTokens the hard cap on the completion, so a model that loops is cut off instead of running to the
 *     timeout; positive
 */
public record OutputLimit(int expectedTokens, int capTokens) {

    /**
     * The limit for translating one masked source.
     *
     * @param maskedSource the text shown to the model, placeholder tokens included; never null
     * @param sourceTag the source language tag, or null when unknown
     * @param targetTag the target language tag; never null
     * @return the limit, or null when the source has no display text and so no expected output
     */
    public static @Nullable OutputLimit forSource(
            final String maskedSource, @Nullable final String sourceTag, final String targetTag) {
        final int allowance = TokenEstimator.outputAllowance(DisplayText.of(maskedSource), sourceTag, targetTag);
        if (allowance <= 0) {
            return null;
        }
        return new OutputLimit(
                allowance,
                TokenEstimator.outputCap(allowance, Tokens.inOrder(maskedSource).size()));
    }
}
