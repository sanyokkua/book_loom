package ua.bookloom.llm.client.openai;

import java.time.Duration;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.llm.dto.OpenAiChatResponse;

/** Reads the token figures of an OpenAI-compatible reply. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OpenAiUsage {

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final double MILLIS_PER_SECOND = 1_000.0;

    /**
     * The figures the server reported; {@code elapsed} stands in for a generation time the server did not give. A
     * figure the server did not send stays null, never 0.
     *
     * @return the usage, or null when the server reported neither a token count nor a timing of its own
     */
    static @Nullable TokenUsage of(
            final OpenAiChatResponse.@Nullable Usage usage,
            final OpenAiChatResponse.@Nullable Stats stats,
            final OpenAiChatResponse.@Nullable Timings timings,
            final Duration elapsed) {
        final boolean counted = usage != null && (usage.promptTokens() != null || usage.completionTokens() != null);
        final Duration promptEval = promptEval(stats, timings);
        if (!counted && promptEval == null) {
            return null;
        }
        final Integer completion = usage == null ? null : usage.completionTokens();
        final Duration generation = generation(stats, timings, completion);
        return new TokenUsage(
                usage == null ? null : usage.promptTokens(),
                completion,
                generation == null ? elapsed : generation,
                promptEval,
                cachedPrompt(usage, timings));
    }

    // LM Studio's time to first token, else llama-server's prompt reading time.
    private static @Nullable Duration promptEval(
            final OpenAiChatResponse.@Nullable Stats stats, final OpenAiChatResponse.@Nullable Timings timings) {
        if (stats != null && stats.timeToFirstToken() != null) {
            return seconds(stats.timeToFirstToken());
        }
        return timings == null || timings.promptMs() == null ? null : seconds(timings.promptMs() / MILLIS_PER_SECOND);
    }

    private static @Nullable Integer cachedPrompt(
            final OpenAiChatResponse.@Nullable Usage usage, final OpenAiChatResponse.@Nullable Timings timings) {
        final OpenAiChatResponse.PromptTokensDetails details = usage == null ? null : usage.promptTokensDetails();
        if (details != null && details.cachedTokens() != null) {
            return details.cachedTokens();
        }
        return timings == null ? null : timings.cacheN();
    }

    // The server's own generation time, else the time its rate gives for the counted tokens; null when it says neither.
    private static @Nullable Duration generation(
            final OpenAiChatResponse.@Nullable Stats stats,
            final OpenAiChatResponse.@Nullable Timings timings,
            final @Nullable Integer completion) {
        if (stats != null && stats.generationTime() != null) {
            return seconds(stats.generationTime());
        }
        if (timings != null && timings.predictedMs() != null) {
            return seconds(timings.predictedMs() / MILLIS_PER_SECOND);
        }
        final Double rate = rate(stats, timings);
        return rate == null || completion == null || rate <= 0 ? null : seconds(completion / rate);
    }

    private static @Nullable Double rate(
            final OpenAiChatResponse.@Nullable Stats stats, final OpenAiChatResponse.@Nullable Timings timings) {
        if (stats != null && stats.tokensPerSecond() != null) {
            return stats.tokensPerSecond();
        }
        return timings == null ? null : timings.predictedPerSecond();
    }

    private static Duration seconds(final double value) {
        return Duration.ofNanos(Math.round(value * NANOS_PER_SECOND));
    }
}
