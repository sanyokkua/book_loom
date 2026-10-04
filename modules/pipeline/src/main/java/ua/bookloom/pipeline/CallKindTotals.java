package ua.bookloom.pipeline;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.ModelCallFinished;

/**
 * Adds up what a run's model calls cost, by what each call was for, so a person can see whether the wall time goes to
 * evaluating prompts or to generating replies and which kind of call spends it. Fed one finished attempt at a time on
 * the job thread, which is the only thread that touches it; it holds counts and times, never text.
 */
public final class CallKindTotals {

    private final Map<CallKind, Mutable> byKind = new EnumMap<>(CallKind.class);

    /**
     * What the calls of one kind cost in all.
     *
     * @param attempts the attempts that went out, answered or failed
     * @param failed how many of them failed
     * @param promptTokens the prompt tokens the provider reported, zero when it reported none
     * @param completionTokens the completion tokens reported or estimated
     * @param elapsed the wall-clock time of all attempts
     * @param promptEval the provider-reported prompt-evaluation time; zero when it reports none
     * @param generation the provider-reported generation time, or the attempt's wall time when it reported none
     * @param cachedPromptTokens the prompt tokens the provider says its cache served
     */
    public record Totals(
            int attempts,
            int failed,
            long promptTokens,
            long completionTokens,
            Duration elapsed,
            Duration promptEval,
            Duration generation,
            long cachedPromptTokens) {}

    /**
     * Counts one finished attempt.
     *
     * @param finished the attempt's announcement, answered or failed
     */
    public void record(final ModelCallFinished finished) {
        Objects.requireNonNull(finished, "finished");
        final Mutable totals = byKind.computeIfAbsent(finished.kind(), kind -> new Mutable());
        totals.attempts++;
        totals.elapsed = totals.elapsed.plus(finished.elapsed());
        if (!finished.isAnswered()) {
            totals.failed++;
            return;
        }
        final @Nullable TokenUsage usage = finished.usage();
        if (usage == null) {
            return;
        }
        totals.add(usage, finished.elapsed());
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
        long promptTokens;
        long completionTokens;
        long cachedPromptTokens;
        Duration elapsed = Duration.ZERO;
        Duration promptEval = Duration.ZERO;
        Duration generation = Duration.ZERO;

        void add(final TokenUsage usage, final Duration wall) {
            promptTokens += usage.prompt() == null ? 0 : usage.prompt();
            completionTokens += usage.completion() == null ? 0 : usage.completion();
            cachedPromptTokens += usage.cachedPrompt() == null ? 0 : usage.cachedPrompt();
            promptEval = promptEval.plus(usage.promptEval() == null ? Duration.ZERO : usage.promptEval());
            generation = generation.plus(usage.generation() == null ? wall : usage.generation());
        }

        Totals snapshot() {
            return new Totals(
                    attempts,
                    failed,
                    promptTokens,
                    completionTokens,
                    elapsed,
                    promptEval,
                    generation,
                    cachedPromptTokens);
        }
    }
}
