package ua.bookloom.llm.http;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.llm.ProviderConfig;

/**
 * The failure of a chat reply that arrived but cannot be read: an empty body, JSON that is not the dialect's reply, or
 * a reply with no message. The server answered, so it is the AI program's fault ({@link ErrorCode#upstream}, retried
 * and waited out like an HTTP 5xx), never an unexpected fault of BookLoom's and never "cannot reach".
 */
@Slf4j
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UnreadableReply {

    /**
     * The typed failure, logged once at WARN with the failure's type and never the body.
     *
     * @param config the provider that answered
     * @param modelId the model asked, or {@code null} when none was
     * @param cause the parse failure, or {@code null} when the reply parsed but held no message
     * @return the {@code upstream} error with the endpoint and model as its safe details
     */
    public static AppError error(
            final ProviderConfig config, final @Nullable String modelId, final @Nullable Throwable cause) {
        Objects.requireNonNull(config, "config");
        log.warn(
                "Chat reply unreadable provider={} host={} modelPresent={} failureType={} code={}",
                config.id(),
                config.baseUrl().getHost(),
                modelId != null,
                cause == null ? "none" : cause.getClass().getSimpleName(),
                ErrorCode.upstream);
        final SafeDetails details = SafeDetails.empty().withEndpoint(config.baseUrl());
        return AppError.of(
                ErrorCode.upstream,
                "The reply could not be read",
                "The AI program answered, but its reply could not be read.",
                (modelId == null ? details : details.withModelName(modelId)).render(),
                cause);
    }
}
