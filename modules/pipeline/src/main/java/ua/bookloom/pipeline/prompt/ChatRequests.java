package ua.bookloom.pipeline.prompt;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ModelWindow;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.api.llm.SourceSize;
import ua.bookloom.pipeline.context.PromptPlan;

/** The one place a generation request gets its settings, so no call leaves without a context size. */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ChatRequests {

    /**
     * The sampling seed of a call that follows a refused or faulted reply: the same request at the same sampling would
     * repeat the same answer. The value is arbitrary; it only differs from none.
     */
    public static final int RETRY_SEED = 8191;

    // Every call BookLoom makes asks for thinking off; the model's profile and the person's thinking choice on
    // Settings › Models decide what is sent (design D20).
    private static final ReasoningLevel INTENT = ReasoningLevel.OFF;

    /**
     * Builds a request for a named call with its temperature, the reasoning intent Off and the size of the source it
     * sends. No sampler beyond the temperature is set here: the model's profile, or the person's Custom values for the
     * model, decide them at the model (design D20).
     *
     * @param name the call's template name; never null
     * @param messages the conversation; never null
     * @param limit the expected completion length and its cap, or null when the call states no expected output
     * @param lowerTemperature whether a retry asks for the name's lower temperature
     * @return the request
     */
    public static ChatRequest build(
            final PromptName name,
            final List<ChatMessage> messages,
            @Nullable final OutputLimit limit,
            final boolean lowerTemperature) {
        Objects.requireNonNull(name, "name");
        log.debug(
                "Prompt breakdown call={} {}",
                name.callKind(),
                PromptBreakdown.of(messages).describe());
        final @Nullable SourceSize source = limit == null ? null : limit.source();
        log.debug("Request call={} intent={} source={}", name.callKind(), INTENT, source);
        return new ChatRequest(
                messages,
                name.temperature(lowerTemperature),
                name.responseSchema().isEmpty()
                        ? null
                        : new ResponseFormat(name.responseFormatName(), name.responseSchema()),
                INTENT,
                ModelWindow.DEFAULT_WINDOW,
                limit == null ? null : limit.expectedTokens(),
                limit == null ? null : limit.capTokens(),
                null,
                name.callKind(),
                null,
                null,
                source,
                null,
                false);
    }

    /**
     * Builds the request of a call the context builder planned: its system blocks as one system message and its user
     * blocks as one last user message, sized for the plan's window and capped at the plan's output reserve, never above
     * the runaway guard of {@code limit} and never below the short-source floor, since a one-word reply once came back
     * empty when the JSON envelope shared a smaller cap with it.
     *
     * @param name the call's template name; never null
     * @param plan the call's plan; never null
     * @param limit the expected completion length and its runaway cap; never null
     * @param lowerTemperature whether a retry asks for the name's lower temperature
     * @return the request
     */
    public static ChatRequest build(
            final PromptName name, final PromptPlan plan, final OutputLimit limit, final boolean lowerTemperature) {
        Objects.requireNonNull(limit, "limit");
        final List<ChatMessage> messages = messagesOf(plan);
        final int cap =
                Math.min(limit.capTokens(), Math.max(plan.maxOutputTokens(), OutputLimit.SHORT_SOURCE_CAP_FLOOR));
        final OutputLimit capped =
                new OutputLimit(Math.min(limit.expectedTokens(), cap), limit.capTokens(), limit.source());
        final ChatRequest built = build(name, messages, capped, lowerTemperature);
        log.debug(
                "Planned request call={} contextSize={} reserve={} guard={} cap={}",
                name.callKind(),
                plan.contextSize(),
                plan.maxOutputTokens(),
                limit.capTokens(),
                cap);
        return built.sizedTo(plan.contextSize(), cap);
    }

    /**
     * A plan's messages: its system blocks as one system message, its user blocks as one last user message.
     *
     * @param plan the non-null plan
     * @return the two messages; never null
     */
    public static List<ChatMessage> messagesOf(final PromptPlan plan) {
        Objects.requireNonNull(plan, "plan");
        return List.of(
                new ChatMessage(ChatRole.SYSTEM, plan.systemText()), new ChatMessage(ChatRole.USER, plan.userText()));
    }
}
