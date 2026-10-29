package ua.bookloom.llm;

import com.google.inject.Singleton;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.llm.provider.ReasoningHeadroom;

/**
 * Writes one WARN per provider and model when an OpenAI-compatible server answers with more tokens than the answer cap
 * and stops by itself: the server ignores the cap, so a runaway reply would not be cut. The reply is used unchanged.
 * One instance is shared by every model the factory makes, so the line is once per process.
 */
@Slf4j
@Singleton
public final class CapIgnoredLog {

    private final Set<String> seen = ConcurrentHashMap.newKeySet();

    /**
     * Writes the WARN when the reply shows the cap ignored and this provider and model have not shown it before.
     *
     * @param providerId the provider's id; may be empty for a model made without one
     * @param modelId the model's id
     * @param kind the dialect that answered; only {@link ProviderKind#OPENAI_COMPATIBLE} is checked
     * @param request the request as sent, with its cap
     * @param response the successful reply
     */
    void noteIfIgnored(
            final String providerId,
            final String modelId,
            final ProviderKind kind,
            final ChatRequest request,
            final ChatResponse response) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(modelId, "modelId");
        if (kind != ProviderKind.OPENAI_COMPATIBLE || !isIgnored(request, response)) {
            return;
        }
        final String token = FailureReason.CAP_IGNORED.token();
        if (seen.add(providerId + "|" + modelId)) {
            log.warn(
                    "Server wrote more tokens than the answer cap reason={} provider={} model={} cap={}",
                    token,
                    providerId,
                    modelId,
                    request.maxOutputTokens());
        } else {
            log.debug("Cap ignored again reason={} provider={} model={}", token, providerId, modelId);
        }
    }

    // A thinking call is sent with room for its reasoning on top of the cap, so that is the limit it may reach.
    private static boolean isIgnored(final ChatRequest request, final ChatResponse response) {
        final @Nullable Integer cap = request.maxOutputTokens();
        final @Nullable TokenUsage usage = response.usage();
        final @Nullable Integer written = usage == null ? null : usage.completion();
        if (cap == null || written == null || response.finishReason() != FinishReason.STOP) {
            return false;
        }
        final ReasoningLevel level = request.reasoningEnabled();
        final @Nullable Integer limit = level != null && level.isThinking() ? ReasoningHeadroom.raise(cap) : cap;
        return limit != null && written > limit;
    }
}
