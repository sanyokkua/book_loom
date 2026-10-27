package ua.bookloom.llm.provider;

import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.SamplingParams;

/** Removes one refused setting from a request and keeps everything else. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SettingDrops {

    /**
     * Drops one setting.
     *
     * @param request the request; non-null
     * @param setting the setting to drop; non-null
     * @return the request without it, or null when the request does not carry it
     */
    public static @Nullable ChatRequest without(final ChatRequest request, final RejectedCapability setting) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(setting, "setting");
        return switch (setting) {
            case STRUCTURED_OUTPUT -> request.responseFormat() == null ? null : request.withoutResponseFormat();
            case REASONING_CONTROL -> request.reasoningEnabled() == null ? null : request.withoutReasoning();
            case TEMPERATURE ->
                request.temperature() == null
                        ? null
                        : request.withModelSettings(null, request.maxOutputTokens(), request.outputField());
            case TOP_P, TOP_K, MIN_P, REPEAT_PENALTY -> withoutSampling(request, setting);
            case SEED -> request.seed() == null ? null : withSeed(request, null);
            case NUM_CTX -> request.contextWindow() == null ? null : withContextWindow(request, null);
            case OUTPUT_FIELD -> withoutMaxTokens(request);
        };
    }

    private static @Nullable ChatRequest withoutMaxTokens(final ChatRequest request) {
        if (request.maxOutputTokens() == null || request.outputField() == OutputLengthField.MAX_COMPLETION_TOKENS) {
            return null;
        }
        return request.withModelSettings(
                request.temperature(), request.maxOutputTokens(), OutputLengthField.MAX_COMPLETION_TOKENS);
    }

    private static @Nullable ChatRequest withoutSampling(final ChatRequest request, final RejectedCapability setting) {
        final SamplingParams s = request.samplingOrNone();
        final boolean carried =
                switch (setting) {
                    case TOP_P -> s.topP() != null;
                    case TOP_K -> s.topK() != null;
                    case MIN_P -> s.minP() != null;
                    default -> s.repeatPenalty() != null;
                };
        if (!carried) {
            return null;
        }
        return request.withSampling(new SamplingParams(
                setting == RejectedCapability.TOP_P ? null : s.topP(),
                setting == RejectedCapability.TOP_K ? null : s.topK(),
                setting == RejectedCapability.MIN_P ? null : s.minP(),
                setting == RejectedCapability.REPEAT_PENALTY ? null : s.repeatPenalty()));
    }

    /**
     * Drops every setting in a set.
     *
     * @param request the request; non-null
     * @param settings the settings to drop; non-null
     * @return the request without them; the same request when it carries none of them
     */
    public static ChatRequest stripAll(final ChatRequest request, final Set<RejectedCapability> settings) {
        Objects.requireNonNull(settings, "settings");
        ChatRequest stripped = Objects.requireNonNull(request, "request");
        for (final RejectedCapability setting : settings) {
            final ChatRequest next = without(stripped, setting);
            stripped = next == null ? stripped : next;
        }
        return stripped;
    }

    private static ChatRequest withSeed(final ChatRequest r, final @Nullable Integer seed) {
        return new ChatRequest(
                r.messages(),
                r.temperature(),
                r.responseFormat(),
                r.reasoningEnabled(),
                r.contextWindow(),
                r.expectedOutputTokens(),
                r.maxOutputTokens(),
                seed,
                r.callKind(),
                r.sampling(),
                r.outputField());
    }

    private static ChatRequest withContextWindow(final ChatRequest r, final @Nullable Integer window) {
        return new ChatRequest(
                r.messages(),
                r.temperature(),
                r.responseFormat(),
                r.reasoningEnabled(),
                window,
                r.expectedOutputTokens(),
                r.maxOutputTokens(),
                r.seed(),
                r.callKind(),
                r.sampling(),
                r.outputField());
    }
}
