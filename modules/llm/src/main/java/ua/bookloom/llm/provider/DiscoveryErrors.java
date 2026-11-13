package ua.bookloom.llm.provider;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;

/** Decides which model-discovery failures both dialect clients degrade to {@link ErrorCode#discoveryFailed}. */
// Checkstyle reads source before Lombok generates the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DiscoveryErrors {

    /**
     * Reports whether a discovery failure's own code should reach the caller unchanged.
     *
     * @param code the error code a discovery call failed with
     * @return {@code true} when the code is preserved as-is, {@code false} when it degrades to
     *     {@link ErrorCode#discoveryFailed}
     */
    public static boolean preserve(ErrorCode code) {
        return switch (code) {
            case auth, unreachable, timeout, cancelled, missingCredential -> true;
            case rateLimited,
                    modelNotFound,
                    modelUnavailable,
                    discoveryFailed,
                    upstream,
                    contextWindow,
                    emptyCompletion,
                    validation,
                    internal,
                    busy -> false;
        };
    }

    /**
     * Degrades a discovery failure whose code is not preserved, keeping its details and cause, with one WARN line
     * naming its original code and reason.
     *
     * @param dialect the client's name for the log line, such as {@code Ollama}
     * @param host the endpoint's host, never its full address
     * @param error the failure the listing answered
     * @param message the sentence the degraded failure is shown with
     * @return the {@code discoveryFailed} failure
     */
    public static AppError degraded(
            final String dialect, final String host, final AppError error, final String message) {
        Objects.requireNonNull(error, "error");
        log.warn(
                "{} discovery degraded host={} originalCode={} reason={} code={}",
                dialect,
                host,
                error.code(),
                FailureReason.tokenIn(error.details()),
                ErrorCode.discoveryFailed);
        return AppError.of(
                ErrorCode.discoveryFailed, "Model discovery failed", message, error.details(), error.cause());
    }
}
