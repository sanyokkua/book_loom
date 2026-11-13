package ua.bookloom.llm.client.openai;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.llm.ProviderConfig;

/** The one failure the OpenAI-compatible client words itself: something unexpected inside the client. */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OpenAiFailures {

    /**
     * Logs and words a failure no reply explains; the details hold only the endpoint and the model name.
     *
     * @param operation what the client was doing, such as {@code chat}
     * @param modelId the model asked, or null when the operation asks none
     * @param failure the cause, kept for the log only, or null when there is none
     * @return an {@code internal} error
     */
    static AppError unexpected(
            final ProviderConfig config,
            final String operation,
            final @Nullable String modelId,
            final @Nullable Throwable failure) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(operation, "operation");
        log.error(
                "Unexpected OpenAI-compatible client failure operation={} host={} modelPresent={} failureType={}",
                operation,
                config.baseUrl().getHost(),
                modelId != null,
                failure == null ? "none" : failure.getClass().getSimpleName());
        final SafeDetails endpoint = SafeDetails.empty().withEndpoint(config.baseUrl());
        return AppError.of(
                ErrorCode.internal,
                "OpenAI-compatible request failed",
                "The OpenAI-compatible provider could not complete the request.",
                (modelId == null ? endpoint : endpoint.withModelName(modelId)).render(),
                failure);
    }
}
