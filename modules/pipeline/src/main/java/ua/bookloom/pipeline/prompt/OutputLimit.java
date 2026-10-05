package ua.bookloom.pipeline.prompt;

import java.util.List;
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

    // A reviewer reply is, per pair, an id and a status, and for a defect a few short edits; a rewrite repeats the
    // whole
    // candidate. The cap lets every pair be rewritten, so a reply that loops is cut off but a full answer is not.
    private static final int REVIEW_BASE_TOKENS = 64;
    private static final int REVIEW_TOKENS_PER_PAIR = 96;
    private static final int EXPECTED_SHARE_DIVISOR = 2;
    // A one-word source under the estimator's 64-token floor came back as an empty target ("Model returned no
    // translated text"): the JSON envelope and any reasoning preamble share the cap with the word itself.
    private static final int SHORT_SOURCE_CAP_FLOOR = 128;
    // Per item the reply spends tokens on the id and its wrapper: {"id":"12","target":""}.
    private static final int BATCH_ITEM_ENVELOPE_TOKENS = 16;
    // A summary reply holds the summary in both languages, each kept under the prompt's 150 words, and a few facts.
    private static final int SUMMARY_EXPECTED_TOKENS = 600;
    private static final int SUMMARY_CAP_TOKENS = 1024;
    // A pre-scan reply holds at most one short entry per candidate sent: the term, its type, gender, a note and a
    // confidence.
    private static final int PRESCAN_BASE_TOKENS = 64;
    private static final int PRESCAN_TOKENS_PER_CANDIDATE = 48;
    // A suggestion reply holds one short item per term: the term, a rendering of a few words and a gender word; a
    // non-Latin rendering costs more tokens than its letters suggest, hence the margin.
    private static final int SUGGEST_BASE_TOKENS = 128;
    private static final int SUGGEST_TOKENS_PER_TERM = 40;
    // A garbled-word reply is empty for clean text and otherwise holds a short entry (id, word, quote) per doubtful
    // word; a text rarely holds more than one or two, so a few entries' worth per text is a generous cap.
    private static final int WORDS_BASE_TOKENS = 128;
    private static final int WORDS_TOKENS_PER_TEXT = 96;

    /**
     * The limit for one reviewer call.
     *
     * @param maskedCandidates the batch's candidates, which a rewrite may repeat whole; never empty
     * @param targetTag the target language tag; never null
     * @return the limit, whose cap covers an edit list for every pair and the whole text of every candidate and whose
     *     expected length is half the cap
     */
    public static OutputLimit forReview(final List<String> maskedCandidates, final String targetTag) {
        if (maskedCandidates.isEmpty()) {
            throw new IllegalArgumentException("a review needs at least one candidate");
        }
        long cap = REVIEW_BASE_TOKENS;
        for (final String candidate : maskedCandidates) {
            cap += REVIEW_TOKENS_PER_PAIR + TokenEstimator.estimate(candidate, targetTag);
        }
        final int capped = (int) Math.min(Integer.MAX_VALUE, cap);
        return new OutputLimit(capped / EXPECTED_SHARE_DIVISOR, capped);
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
     * The limit for one target-suggestion batch.
     *
     * @param termCount how many terms the batch sends; at least one
     * @return the limit, growing with the terms, whose expected length is half the cap
     */
    public static OutputLimit forSuggestions(final int termCount) {
        if (termCount < 1) {
            throw new IllegalArgumentException("termCount must be positive: " + termCount);
        }
        final int cap = SUGGEST_BASE_TOKENS + SUGGEST_TOKENS_PER_TERM * termCount;
        return new OutputLimit(cap / EXPECTED_SHARE_DIVISOR, cap);
    }

    /**
     * The limit for one garbled-word call.
     *
     * @param textCount how many texts the call sends; at least one
     * @return the limit, growing with the texts, whose expected length is half the cap
     */
    public static OutputLimit forSuspiciousWords(final int textCount) {
        if (textCount < 1) {
            throw new IllegalArgumentException("textCount must be positive: " + textCount);
        }
        final int cap = WORDS_BASE_TOKENS + WORDS_TOKENS_PER_TEXT * textCount;
        return new OutputLimit(cap / EXPECTED_SHARE_DIVISOR, cap);
    }

    /**
     * The limit for one batch draft: every item's allowance plus what its id and wrapper cost, under one
     * cap, so a reply that loops is cut off while a full batch is not.
     *
     * @param maskedSources the batch's masked sources; never empty
     * @param sourceTag the source language tag, or null when unknown
     * @param targetTag the target language tag; never null
     * @return the limit, whose cap is never below 128 tokens
     */
    public static OutputLimit forBatch(
            final List<String> maskedSources, @Nullable final String sourceTag, final String targetTag) {
        if (maskedSources.isEmpty()) {
            throw new IllegalArgumentException("a batch needs at least one item");
        }
        int allowance = 0;
        int placeholders = 0;
        for (final String masked : maskedSources) {
            allowance += TokenEstimator.outputAllowance(DisplayText.of(masked), sourceTag, targetTag)
                    + BATCH_ITEM_ENVELOPE_TOKENS;
            placeholders += Tokens.inOrder(masked).size();
        }
        return new OutputLimit(
                allowance, Math.max(SHORT_SOURCE_CAP_FLOOR, TokenEstimator.outputCap(allowance, placeholders)));
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
