package ua.bookloom.llm;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.OutputLengthField;

/** How a model's stored answer-length settings combine with what one call asks for. */
// Checkstyle reads source before Lombok generates the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class StoredLimits {

    // The person's limit caps a call that states none; where both exist the smaller wins.
    static @Nullable Integer capped(@Nullable Integer requestCap, @Nullable Integer maxOutput) {
        if (requestCap == null) {
            return maxOutput;
        }
        return maxOutput == null ? requestCap : Integer.valueOf(Math.min(requestCap, maxOutput));
    }

    static @Nullable OutputLengthField outputField(ChatRequest request, ModelSettings stored) {
        return request.outputField() == null && stored.outputField() != OutputLengthField.MAX_TOKENS
                ? stored.outputField()
                : request.outputField();
    }
}
