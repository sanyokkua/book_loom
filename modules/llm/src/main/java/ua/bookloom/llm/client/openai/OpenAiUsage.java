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

    /** The figures the server reported, or null when it reported no token count; {@code elapsed} stands in for the generation time. */
    static @Nullable TokenUsage of(
            final OpenAiChatResponse.@Nullable Usage usage,
            final OpenAiChatResponse.@Nullable Stats stats,
            final Duration elapsed) {
        if (usage == null || (usage.promptTokens() == null && usage.completionTokens() == null)) {
            return null;
        }
        final Double generation = stats == null ? null : stats.generationTime();
        final Double firstToken = stats == null ? null : stats.timeToFirstToken();
        final OpenAiChatResponse.PromptTokensDetails details = usage.promptTokensDetails();
        return new TokenUsage(
                usage.promptTokens(),
                usage.completionTokens(),
                generation == null ? elapsed : seconds(generation),
                firstToken == null ? null : seconds(firstToken),
                details == null ? null : details.cachedTokens());
    }

    private static Duration seconds(final double value) {
        return Duration.ofNanos(Math.round(value * NANOS_PER_SECOND));
    }
}
