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

    // A judge reply is a score, a verdict word and a few short findings per pair; these bound it so a reply that loops
    // is cut off long before it fills the context, which is what kept a re-judge waiting for the full timeout.
    private static final int JUDGE_BASE_TOKENS = 128;
    private static final int JUDGE_TOKENS_PER_PAIR = 192;
    private static final int JUDGE_MAX_CAP_TOKENS = 1024;
    private static final int EXPECTED_SHARE_DIVISOR = 2;

    /**
     * The limit for one judge call.
     *
     * @param pairCount how many pairs the call scores; at least one
     * @return the limit, whose cap grows with the pairs up to 1024 tokens and whose expected length is half the cap
     */
    public static OutputLimit forJudge(final int pairCount) {
        if (pairCount < 1) {
            throw new IllegalArgumentException("pairCount must be positive: " + pairCount);
        }
        final long uncapped = JUDGE_BASE_TOKENS + (long) JUDGE_TOKENS_PER_PAIR * pairCount;
        final int cap = (int) Math.min(JUDGE_MAX_CAP_TOKENS, uncapped);
        return new OutputLimit(cap / EXPECTED_SHARE_DIVISOR, cap);
    }

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
