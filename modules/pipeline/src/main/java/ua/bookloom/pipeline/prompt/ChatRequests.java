package ua.bookloom.pipeline.prompt;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.pipeline.chunk.TokenBudget;

/** The one place a generation request gets its settings, so no call leaves without a context size. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ChatRequests {

    /**
     * Builds a request for a named call.
     *
     * @param name the call's template name; never null
     * @param messages the conversation; never null
     * @param expectedOutputTokens the expected completion length that scales the timeout, or null when unknown
     * @param lowerTemperature whether a retry asks for the name's lower temperature
     * @return the request, with reasoning off and the effective context size
     */
    public static ChatRequest build(
            final PromptName name,
            final List<ChatMessage> messages,
            @Nullable final Integer expectedOutputTokens,
            final boolean lowerTemperature) {
        Objects.requireNonNull(name, "name");
        return new ChatRequest(
                messages,
                name.temperature(lowerTemperature),
                new ResponseFormat(name.responseFormatName(), DraftSchema.SCHEMA),
                false,
                TokenBudget.EFFECTIVE_CONTEXT,
                expectedOutputTokens);
    }
}
