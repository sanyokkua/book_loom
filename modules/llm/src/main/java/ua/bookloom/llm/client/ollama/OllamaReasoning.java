package ua.bookloom.llm.client.ollama;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ReasoningLevel;

/**
 * How Ollama's native request words the reasoning level the model's profile decided: {@code false}, its lowest level
 * {@code "low"}, or {@code true}. The room a thinking call needs is already in the request's cap.
 */
// Checkstyle parses source before Lombok runs, so it cannot see the private constructor generated below.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OllamaReasoning {

    /** Ollama's native {@code think} value, or null when no control is sent. */
    static @Nullable Object thinkControl(final @Nullable ReasoningLevel level) {
        if (level == null) {
            return null;
        }
        return switch (level) {
            case ON -> true;
            case LOW -> "low";
            case OFF -> false;
        };
    }
}
