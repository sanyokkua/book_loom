package ua.bookloom.pipeline.prompt;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.pipeline.chunk.TokenEstimator;

/**
 * Where a prompt's tokens go: the system message — the static prefix a server can cache across a run's calls — against
 * everything that varies with the call. Estimated, so it is comparable across calls rather than exact.
 *
 * @param systemTokens the estimated tokens of the system messages
 * @param otherTokens the estimated tokens of every other message
 */
public record PromptBreakdown(int systemTokens, int otherTokens) {

    // The system message is English prose, as every prompt is; the rest mixes the source and target languages.
    private static final String SYSTEM_LANGUAGE = "en";

    /**
     * Measures a conversation.
     *
     * @param messages the non-null conversation as sent
     * @return the split of its estimated tokens
     */
    public static PromptBreakdown of(final List<ChatMessage> messages) {
        Objects.requireNonNull(messages, "messages");
        int system = 0;
        int other = 0;
        for (final ChatMessage message : messages) {
            if (message.role() == ChatRole.SYSTEM) {
                system += TokenEstimator.estimate(message.content(), SYSTEM_LANGUAGE);
            } else {
                other += TokenEstimator.estimate(message.content(), null);
            }
        }
        return new PromptBreakdown(system, other);
    }

    /** All the prompt's estimated tokens. */
    public int total() {
        return systemTokens + otherTokens;
    }

    /** The static prefix's share of the prompt, 0 when the prompt is empty. */
    public double prefixShare() {
        return total() == 0 ? 0 : (double) systemTokens / total();
    }

    /** The one DEBUG line that shows the split. */
    public String describe() {
        return String.format(
                Locale.ROOT,
                "systemTokens=%d otherTokens=%d prefixShare=%.0f%%",
                systemTokens,
                otherTokens,
                prefixShare() * 100);
    }
}
