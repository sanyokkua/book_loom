package ua.bookloom.pipeline.prompt;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.pipeline.chunk.TokenBudget;

/** The one place a generation request gets its settings, so no call leaves without a context size. */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ChatRequests {

    /**
     * Builds a request for a named call with the run's resolved reasoning and sampling defaults. A call carries the
     * level its plan names, so a stored Low reaches the server as Low.
     *
     * @param name the call's template name; never null
     * @param messages the conversation; never null
     * @param limit the expected completion length and its cap, or null when the call states no expected output
     * @param lowerTemperature whether a retry asks for the name's lower temperature
     * @param defaults the run's reasoning plan and sampling mode; never null
     * @return the request
     */
    public static ChatRequest build(
            final PromptName name,
            final List<ChatMessage> messages,
            @Nullable final OutputLimit limit,
            final boolean lowerTemperature,
            final RequestDefaults defaults) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(defaults, "defaults");
        log.debug(
                "Prompt breakdown call={} {}",
                name.callKind(),
                PromptBreakdown.of(messages).describe());
        final SamplingMode mode = defaults.sampling();
        log.debug("Sampling call={} mode={}", name.callKind(), mode.token());
        final ReasoningLevel level = defaults.reasoningFor(name);
        log.debug("Reasoning call={} level={}", name.callKind(), level);
        return new ChatRequest(
                messages,
                name.temperature(lowerTemperature),
                name.responseSchema().isEmpty()
                        ? null
                        : new ResponseFormat(name.responseFormatName(), name.responseSchema()),
                level,
                TokenBudget.EFFECTIVE_CONTEXT,
                limit == null ? null : limit.expectedTokens(),
                limit == null ? null : limit.capTokens(),
                null,
                name.callKind(),
                SamplingProfile.paramsFor(name, mode));
    }
}
