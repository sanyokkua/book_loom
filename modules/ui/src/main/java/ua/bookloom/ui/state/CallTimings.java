package ua.bookloom.ui.state;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The timing figures of a call as the window words them. A figure the engine did not report is a dash, never 0, and
 * is left out of an average, so a server that reports none cannot look instant.
 */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CallTimings {

    private static final double PERCENT = 100.0;
    private static final double MILLIS_PER_SECOND = 1000.0;

    /**
     * The glyph of a figure that was not reported.
     *
     * @param messages the catalogue the glyph comes from
     * @return the dash
     */
    public static String dash(final Messages messages) {
        return messages.get(MessageKey.SETTINGS_TEST_METRIC_NONE);
    }

    /**
     * The time to first token (the prompt-reading time).
     *
     * @param messages the catalogue the words come from
     * @param usage the call's usage, or {@code null}
     * @return the time, or the dash when the engine did not report it
     */
    public static String firstToken(final Messages messages, final @Nullable TokenUsage usage) {
        final @Nullable Duration read = usage == null ? null : usage.promptEval();
        return read == null ? dash(messages) : CheckSentences.durationText(messages, read);
    }

    /**
     * The tokens written a second.
     *
     * @param messages the catalogue the words come from
     * @param usage the call's usage, or {@code null}
     * @return the speed with its unit, or the dash when the engine did not report it
     */
    public static String speed(final Messages messages, final @Nullable TokenUsage usage) {
        final @Nullable Double rate = tokensPerSecond(usage);
        return rate == null ? dash(messages) : messages.get(MessageKey.SETTINGS_TEST_METRIC_SPEED_VALUE, rate);
    }

    /**
     * The share of the prompt the engine served from its cache.
     *
     * @param messages the catalogue the words come from
     * @param usage the call's usage, or {@code null}
     * @return the share as a percentage, or the dash when it cannot be told
     */
    public static String cached(final Messages messages, final @Nullable TokenUsage usage) {
        final @Nullable Integer share = usage == null ? null : cachedShare(usage.prompt(), usage.cachedPrompt());
        return share == null ? dash(messages) : messages.get(MessageKey.CALL_TIMINGS_SHARE, share);
    }

    /**
     * The three figures of a call in one line.
     *
     * @param messages the catalogue the words come from
     * @param usage the call's usage, or {@code null}
     * @return time to first token, speed and cached share, each a dash when not reported
     */
    public static String line(final Messages messages, final @Nullable TokenUsage usage) {
        return messages.get(
                MessageKey.CALL_TIMINGS, firstToken(messages, usage), speed(messages, usage), cached(messages, usage));
    }

    /**
     * The cached share of a prompt.
     *
     * @param promptTokens the prompt tokens counted, or {@code null} when not reported
     * @param cachedTokens the prompt tokens served from the cache, or {@code null} when not reported
     * @return the rounded percentage, or {@code null} when either figure is missing or the prompt is empty
     */
    public static @Nullable Integer cachedShare(
            final @Nullable Integer promptTokens, final @Nullable Integer cachedTokens) {
        if (promptTokens == null || cachedTokens == null || promptTokens <= 0) {
            return null;
        }
        return (int) Math.round(PERCENT * cachedTokens / promptTokens);
    }

    /**
     * The tokens written a second, from the generation time the engine reported.
     *
     * @param usage the call's usage, or {@code null}
     * @return the rate, or {@code null} when the completion or its time was not reported
     */
    public static @Nullable Double tokensPerSecond(final @Nullable TokenUsage usage) {
        final @Nullable Integer written = usage == null ? null : usage.completion();
        final @Nullable Duration generation = usage == null ? null : usage.generation();
        if (written == null || generation == null || generation.isZero()) {
            return null;
        }
        return written * MILLIS_PER_SECOND / generation.toMillis();
    }

    /**
     * The mean time to first token over the calls that reported one.
     *
     * @param calls the calls
     * @return the mean, or empty when no call reported the figure
     */
    public static Optional<Duration> meanFirstToken(final Collection<CallSnapshot> calls) {
        final var reported = calls.stream()
                .map(CallSnapshot::usage)
                .filter(usage -> usage != null && usage.promptEval() != null)
                .map(usage -> usage.promptEval().toMillis())
                .toList();
        return reported.isEmpty()
                ? Optional.empty()
                : Optional.of(Duration.ofMillis(Math.round(
                        reported.stream().mapToLong(Long::longValue).average().orElse(0))));
    }
}
