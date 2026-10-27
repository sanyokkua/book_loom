package ua.bookloom.llm.client.ollama;

import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.llm.provider.ReasoningHeadroom;

/** How Ollama's native request words the reasoning control and the answer cap. */
// Checkstyle parses source before Lombok runs, so it cannot see the private constructor generated below.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OllamaReasoning {

    /**
     * Ollama's native {@code think} value. A gpt-oss model ignores {@code false} and spends the whole output cap on
     * reasoning, so "reasoning off" is asked of it as its lowest level.
     */
    static @Nullable Object thinkControl(final String modelId, final @Nullable ReasoningLevel reasoningEnabled) {
        if (reasoningEnabled == null) {
            return null;
        }
        return switch (reasoningEnabled) {
            case ON -> true;
            case LOW -> "low";
            case OFF -> modelId.toLowerCase(Locale.ROOT).contains("gpt-oss") ? "low" : false;
        };
    }

    /**
     * The cap the server is given: a call that thinks needs room for it on any model, and a gpt-oss model reasons even
     * at its lowest level, so it gets the room whether or not thinking was asked for (once, never twice).
     */
    static @Nullable Integer withReasoningHeadroom(
            final String modelId, final @Nullable Integer cap, final @Nullable ReasoningLevel reasoningEnabled) {
        final boolean thinks = reasoningEnabled != null && reasoningEnabled.isThinking();
        return thinks || modelId.toLowerCase(Locale.ROOT).contains("gpt-oss") ? ReasoningHeadroom.raise(cap) : cap;
    }
}
