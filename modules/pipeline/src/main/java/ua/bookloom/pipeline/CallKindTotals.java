package ua.bookloom.pipeline;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.CallRecord;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.CallKind;

/**
 * Adds up what model calls cost, by what each call was for, so a person can see whether the wall time goes to
 * evaluating prompts or to generating replies and which kind of call spends it. Each figure is summed only over the
 * calls that reported it, and the count of those calls is kept beside the sum: a server that reports no cached tokens
 * has no cached figure, never a zero. Built on one thread from a list of the ledger's records; it holds counts and
 * times, never text.
 */
@Slf4j
public final class CallKindTotals {

    private final Map<CallKind, Mutable> byKind = new EnumMap<>(CallKind.class);

    /**
     * Sums a list of records.
     *
     * @param records the records, oldest first; never null
     * @return the totals of the records' kinds
     */
    public static CallKindTotals of(final List<CallRecord> records) {
        Objects.requireNonNull(records, "records");
        final CallKindTotals totals = new CallKindTotals();
        records.forEach(totals::record);
        log.debug("Call totals summed records={} kinds={}", records.size(), totals.byKind.keySet());
        return totals;
    }

    /**
     * What the calls of one kind cost in all. Each sum is paired with the count of calls that reported the figure.
     *
     * @param attempts the attempts that went out, answered or failed
     * @param failed how many of them failed
     * @param elapsed the wall-clock time of all attempts
     * @param promptTokens the prompt tokens of the calls that reported them
     * @param promptReported how many calls reported prompt tokens
     * @param completionTokens the completion tokens of the calls that reported them
     * @param completionReported how many calls reported completion tokens
     * @param cachedPromptTokens the prompt tokens the provider says its cache served, over the calls that said
     * @param cachedReported how many calls reported a cache figure
     * @param cachedPromptBase the prompt tokens of just the calls that reported a cache figure, which the cached share
     *     is taken of
     * @param generation the generation time of the calls that reported it
     * @param generationReported how many calls reported a generation time
     * @param promptEval the time before the first token of the calls that had one: the time the call measured, else
     *     the provider's prompt-evaluation time
     * @param firstTokenCalls how many calls had a time before the first token
     * @param firstTokenCallElapsed the wall-clock time of just those calls, so the prefill share is not diluted by
     *     calls that reported nothing
     * @param cost the price of the calls that reported one
     * @param costReported how many calls reported a price
     */
    public record Totals(
            int attempts,
            int failed,
            Duration elapsed,
            long promptTokens,
            int promptReported,
            long completionTokens,
            int completionReported,
            long cachedPromptTokens,
            int cachedReported,
            long cachedPromptBase,
            Duration generation,
            int generationReported,
            Duration promptEval,
            int firstTokenCalls,
            Duration firstTokenCallElapsed,
            BigDecimal cost,
            int costReported) {

        /**
         * The price reported.
         *
         * @return the sum, or empty if no call reported one
         */
        public Optional<BigDecimal> costTotal() {
            return costReported == 0 ? Optional.empty() : Optional.of(cost);
        }

        /**
         * The prompt tokens reported.
         *
         * @return the sum, or empty if no call reported them
         */
        public Optional<Long> promptTotal() {
            return promptReported == 0 ? Optional.empty() : Optional.of(promptTokens);
        }

        /**
         * The completion tokens reported.
         *
         * @return the sum, or empty if no call reported them
         */
        public Optional<Long> completionTotal() {
            return completionReported == 0 ? Optional.empty() : Optional.of(completionTokens);
        }

        /**
         * The prompt tokens served from the provider's cache.
         *
         * @return the sum, or empty if no call reported a cache figure
         */
        public Optional<Long> cachedTotal() {
            return cachedReported == 0 ? Optional.empty() : Optional.of(cachedPromptTokens);
        }

        /**
         * The generation time reported.
         *
         * @return the sum, or empty if no call reported one
         */
        public Optional<Duration> generationTotal() {
            return generationReported == 0 ? Optional.empty() : Optional.of(generation);
        }

        /**
         * The time before the first token, over the calls that had one.
         *
         * @return the sum, or empty if no call had one
         */
        public Optional<Duration> promptEvalTotal() {
            return firstTokenCalls == 0 ? Optional.empty() : Optional.of(promptEval);
        }

        /**
         * The mean time before the first token over the attempts that reported it.
         *
         * @return the mean, or empty when no attempt reported one
         */
        public Optional<Duration> meanFirstToken() {
            return firstTokenCalls == 0 ? Optional.empty() : Optional.of(promptEval.dividedBy(firstTokenCalls));
        }

        /**
         * The share of call time spent before the first token, which is what reordering the prompt could save.
         *
         * @return the share from 0 to 1, or empty when no attempt reported a first-token time
         */
        public Optional<Double> prefillShare() {
            if (firstTokenCalls == 0 || firstTokenCallElapsed.isZero()) {
                return Optional.empty();
            }
            return Optional.of((double) promptEval.toNanos() / firstTokenCallElapsed.toNanos());
        }

        /**
         * The share of the prompt tokens the cache served, over the calls that reported a cache figure.
         *
         * @return the share from 0 to 1, or empty when no call reported both a cache figure and its prompt tokens
         */
        public Optional<Double> cachedShare() {
            return cachedReported == 0 || cachedPromptBase == 0
                    ? Optional.empty()
                    : Optional.of((double) cachedPromptTokens / cachedPromptBase);
        }
    }

    /**
     * Counts one call attempt: a failed one as an attempt and a failure only, an answered one with every figure it
     * reported.
     *
     * @param record the attempt's record
     */
    public void record(final CallRecord record) {
        Objects.requireNonNull(record, "record");
        final Mutable totals = byKind.computeIfAbsent(record.callKind(), kind -> new Mutable());
        totals.attempts++;
        totals.elapsed = totals.elapsed.plus(record.wallTime());
        if (record.isFailed()) {
            totals.failed++;
            return;
        }
        totals.firstToken(firstTokenOf(record), record.wallTime());
        final @Nullable TokenUsage usage = record.usage();
        if (usage != null) {
            totals.add(usage);
        }
        if (record.cost() != null) {
            totals.cost = totals.cost.add(record.cost());
            totals.costReported++;
        }
    }

    // The time the call measured itself wins; the provider's prompt-evaluation time stands in for it otherwise.
    private static @Nullable Duration firstTokenOf(final CallRecord record) {
        final Duration measured = record.timeToFirstToken();
        if (measured != null) {
            return measured;
        }
        final TokenUsage usage = record.usage();
        return usage == null ? null : usage.promptEval();
    }

    /**
     * What has been counted so far.
     *
     * @return the totals of each kind that had an attempt, in the order the kinds are declared; never null
     */
    public Map<CallKind, Totals> totals() {
        final Map<CallKind, Totals> copy = new EnumMap<>(CallKind.class);
        byKind.forEach((kind, totals) -> copy.put(kind, totals.snapshot()));
        return copy;
    }

    private static final class Mutable {

        int attempts;
        int failed;
        Duration elapsed = Duration.ZERO;
        long promptTokens;
        int promptReported;
        long completionTokens;
        int completionReported;
        long cachedPromptTokens;
        int cachedReported;
        long cachedPromptBase;
        Duration generation = Duration.ZERO;
        int generationReported;
        Duration promptEval = Duration.ZERO;
        int firstTokenCalls;
        Duration firstTokenCallElapsed = Duration.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        int costReported;

        void add(final TokenUsage usage) {
            final Integer prompt = usage.prompt();
            if (prompt != null) {
                promptTokens += prompt;
                promptReported++;
            }
            final Integer completion = usage.completion();
            if (completion != null) {
                completionTokens += completion;
                completionReported++;
            }
            final Integer cached = usage.cachedPrompt();
            if (cached != null) {
                cachedPromptTokens += cached;
                cachedReported++;
                cachedPromptBase += prompt == null ? 0 : prompt;
            }
            final Duration generated = usage.generation();
            if (generated != null) {
                generation = generation.plus(generated);
                generationReported++;
            }
        }

        void firstToken(final @Nullable Duration firstToken, final Duration wall) {
            if (firstToken != null) {
                promptEval = promptEval.plus(firstToken);
                firstTokenCalls++;
                firstTokenCallElapsed = firstTokenCallElapsed.plus(wall);
            }
        }

        Totals snapshot() {
            return new Totals(
                    attempts,
                    failed,
                    elapsed,
                    promptTokens,
                    promptReported,
                    completionTokens,
                    completionReported,
                    cachedPromptTokens,
                    cachedReported,
                    cachedPromptBase,
                    generation,
                    generationReported,
                    promptEval,
                    firstTokenCalls,
                    firstTokenCallElapsed,
                    cost,
                    costReported);
        }
    }
}
