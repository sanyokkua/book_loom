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

    /**
     * The figures the server reported; {@code elapsed} stands in for a generation time the server did not give.
     *
     * @return the usage, or null when the server reported neither a token count nor LM Studio's own timing
     */
    static @Nullable TokenUsage of(
            final OpenAiChatResponse.@Nullable Usage usage,
            final OpenAiChatResponse.@Nullable Stats stats,
            final Duration elapsed) {
        final boolean counted = usage != null && (usage.promptTokens() != null || usage.completionTokens() != null);
        final Double firstToken = stats == null ? null : stats.timeToFirstToken();
        if (!counted && firstToken == null) {
            return null;
        }
        final Integer completion = usage == null ? null : usage.completionTokens();
        final OpenAiChatResponse.PromptTokensDetails details = usage == null ? null : usage.promptTokensDetails();
        final Duration generation = generation(stats, completion);
        return new TokenUsage(
                usage == null ? null : usage.promptTokens(),
                completion,
                generation == null ? elapsed : generation,
                firstToken == null ? null : seconds(firstToken),
                details == null ? null : details.cachedTokens());
    }

    // LM Studio's generation time, else the time its rate gives for the counted tokens; null when it says neither.
    private static @Nullable Duration generation(
            final OpenAiChatResponse.@Nullable Stats stats, final @Nullable Integer completion) {
        if (stats == null) {
            return null;
        }
        final Double time = stats.generationTime();
        if (time != null) {
            return seconds(time);
        }
        final Double rate = stats.tokensPerSecond();
        return rate == null || completion == null || rate <= 0 ? null : seconds(completion / rate);
    }

    private static Duration seconds(final double value) {
        return Duration.ofNanos(Math.round(value * NANOS_PER_SECOND));
    }
}
