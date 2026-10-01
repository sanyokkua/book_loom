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
    // A one-word source under the estimator's 64-token floor came back as an empty target ("Model returned no
    // translated text"): the JSON envelope and any reasoning preamble share the cap with the word itself.
    private static final int SHORT_SOURCE_CAP_FLOOR = 128;
    // A reflect reply is a short list of issues, each a note and a suggestion: the prompt asks for at most five. With
    // no
    // cap, gemma4:e4b once streamed 9,664 lines of it until the three-minute timeout.
    private static final int REFLECT_EXPECTED_TOKENS = 256;
    private static final int REFLECT_CAP_TOKENS = 600;
    // A summary reply holds the summary in both languages, each kept under the prompt's 150 words, and a few facts.
    private static final int SUMMARY_EXPECTED_TOKENS = 600;
    private static final int SUMMARY_CAP_TOKENS = 1024;
    // A pre-scan reply holds at most one short entry per candidate sent: the term, its type, gender, a note and a
    // confidence.
    private static final int PRESCAN_BASE_TOKENS = 64;
    private static final int PRESCAN_TOKENS_PER_CANDIDATE = 48;

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
     * The limit for one reflect call, whose reply is a short list of issues whatever the source's length.
     *
     * @return the limit: 600 tokens at most
     */
    public static OutputLimit forReflect() {
        return new OutputLimit(REFLECT_EXPECTED_TOKENS, REFLECT_CAP_TOKENS);
    }

    /**
     * The limit for one rolling-summary call, bounded by the prompt's word budget per language.
     *
     * @return the limit: 1024 tokens at most
     */
    public static OutputLimit forSummary() {
        return new OutputLimit(SUMMARY_EXPECTED_TOKENS, SUMMARY_CAP_TOKENS);
    }

    /**
     * The limit for one pre-scan batch.
     *
     * @param candidateCount how many candidates the batch sends; at least one
     * @return the limit, growing with the candidates, whose expected length is half the cap
     */
    public static OutputLimit forPrescan(final int candidateCount) {
        if (candidateCount < 1) {
            throw new IllegalArgumentException("candidateCount must be positive: " + candidateCount);
        }
        final int cap = PRESCAN_BASE_TOKENS + PRESCAN_TOKENS_PER_CANDIDATE * candidateCount;
        return new OutputLimit(cap / EXPECTED_SHARE_DIVISOR, cap);
    }

    /**
     * The limit for translating one masked source.
     *
     * @param maskedSource the text shown to the model, placeholder tokens included; never null
     * @param sourceTag the source language tag, or null when unknown
     * @param targetTag the target language tag; never null
     * @return the limit, whose cap is never below 128 tokens, or null when the source has no display text and so no
     *     expected output
     */
    public static @Nullable OutputLimit forSource(
            final String maskedSource, @Nullable final String sourceTag, final String targetTag) {
        final int allowance = TokenEstimator.outputAllowance(DisplayText.of(maskedSource), sourceTag, targetTag);
        if (allowance <= 0) {
            return null;
        }
        return new OutputLimit(
                allowance,
                Math.max(
                        SHORT_SOURCE_CAP_FLOOR,
                        TokenEstimator.outputCap(
                                allowance, Tokens.inOrder(maskedSource).size())));
    }
}
