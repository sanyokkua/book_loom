package ua.bookloom.llm.http;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;

/** Applies the shared ordered D4 matrix; response bodies influence classification but never enter errors or logs. */
// Checkstyle reads source before Lombok generates the private constructor; match the repository's documented escape.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HttpErrorMapper {

    private static final Pattern CONTEXT_WINDOW = Pattern.compile(
            "context_length_exceeded|n_ctx|context\\b.*\\b(exceed|too long|greater than)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern OLLAMA_MODEL_NOT_FOUND = Pattern.compile(
            "\"error\"\\s*:\\s*\"[^\"]{0,512}\\bmodel\\b[^\"]{0,256}\\bnot\\s+found\\b[^\"]*\"",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final int HTTP_SUCCESS_MIN = 200;
    private static final int HTTP_SUCCESS_MAX = 299;
    private static final int HTTP_SERVER_ERROR_MIN = 500;
    private static final int HTTP_SERVER_ERROR_MAX = 599;

    /** Call purpose distinguishes the probe's reachability rule from a chat model lookup. */
    public enum CallPurpose {
        /** A provider inference request; an HTTP 404 means the selected model is absent. */
        CHAT,

        /** A reachability check; only authentication and server failures mean the endpoint is unreachable. */
        PROBE,

        /** A model-list or other non-chat endpoint. */
        DISCOVERY
    }

    /** Maps a received status while preserving successful or probe-reachable replies verbatim. */
    public static Result<HttpReply> map(
            HttpReply reply, ProviderConfig config, CallPurpose purpose, @Nullable String modelName) {
        Objects.requireNonNull(reply, "reply");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(purpose, "purpose");
        log.debug(
                "HTTP error mapping input purpose={} host={} status={} bodyLength={} modelPresent={}",
                purpose,
                config.baseUrl().getHost(),
                reply.status(),
                reply.body().length(),
                modelName != null);
        if (isProbeReachable(purpose, reply.status())) {
            return successfulReply(reply, config, purpose, "probe-reachable");
        }
        if (isSuccessful(reply.status()) && !isOllamaModelNotFound(reply, config)) {
            return successfulReply(reply, config, purpose, "successful-response");
        }
        final Mapping mapping = classify(reply, config, purpose);
        final AppError error = statusError(mapping, reply, config, modelName);
        log.debug(
                "HTTP error mapping selected row={} purpose={} code={} host={} status={}",
                mapping.row(),
                purpose,
                error.code(),
                config.baseUrl().getHost(),
                reply.status());
        return Result.err(error);
    }

    private static Result<HttpReply> successfulReply(
            HttpReply reply, ProviderConfig config, CallPurpose purpose, String row) {
        log.debug(
                "HTTP error mapping selected row={} purpose={} host={} status={}",
                row,
                purpose,
                config.baseUrl().getHost(),
                reply.status());
        return Result.ok(reply);
    }

    /** Classifies a transport failure, preserving the cause internally and restoring interrupted status. */
    public static AppError map(Throwable failure, ProviderConfig config, @Nullable String modelName) {
        return map(failure, config, modelName, "UNKNOWN", "");
    }

    static AppError map(
            Throwable failure, ProviderConfig config, @Nullable String modelName, String method, String path) {
        Objects.requireNonNull(failure, "failure");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(path, "path");
        final Mapping mapping = classify(failure);
        final Duration timeout = failure instanceof HttpConnectTimeoutException
                ? config.connectTimeout()
                : mapping.code() == ErrorCode.timeout ? config.requestTimeout() : null;
        final SafeDetails details = transportDetails(config, modelName, timeout);
        final AppError error =
                AppError.of(mapping.code(), mapping.title(), mapping.message(), details.render(), failure);
        if (failure instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        log.debug(
                "HTTP error mapping selected row={} method={} host={} path={} code={} timeout={}",
                mapping.row(),
                method,
                config.baseUrl().getHost(),
                path,
                error.code(),
                timeout,
                failure);
        return error;
    }

    private static boolean isProbeReachable(CallPurpose purpose, int status) {
        return purpose == CallPurpose.PROBE
                && status != 401
                && status != 403
                && (status < HTTP_SERVER_ERROR_MIN || status > HTTP_SERVER_ERROR_MAX);
    }

    private static boolean isSuccessful(int status) {
        return status >= HTTP_SUCCESS_MIN && status <= HTTP_SUCCESS_MAX;
    }

    private static Mapping classify(HttpReply reply, ProviderConfig config, CallPurpose purpose) {
        final int status = reply.status();
        if (status == 401 || status == 403) {
            return authenticationFailure();
        }
        if ((purpose == CallPurpose.CHAT && status == 404) || isOllamaModelNotFound(reply, config)) {
            return modelNotFoundFailure();
        }
        return classifyRemainingStatus(reply, status);
    }

    private static boolean isOllamaModelNotFound(HttpReply reply, ProviderConfig config) {
        return config.kind() == ProviderKind.OLLAMA
                && OLLAMA_MODEL_NOT_FOUND.matcher(reply.body()).find();
    }

    private static Mapping classifyRemainingStatus(HttpReply reply, int status) {
        if (status == 429) {
            return mapping(
                    ErrorCode.rateLimited,
                    "rate-limit",
                    "Provider rate limit reached",
                    "The provider asked the application to slow down.");
        }
        if (status >= HTTP_SERVER_ERROR_MIN && status <= HTTP_SERVER_ERROR_MAX) {
            return mapping(
                    ErrorCode.upstream,
                    "server-error",
                    "Provider server error",
                    "The provider could not complete the request.");
        }
        if (status == 400) {
            return classifyBadRequest(reply.body());
        }
        return mapping(
                ErrorCode.internal,
                "unexpected-status",
                "Unexpected provider response",
                "The provider returned an unexpected response.");
    }

    private static Mapping classifyBadRequest(String body) {
        if (CONTEXT_WINDOW.matcher(body).find()) {
            return mapping(
                    ErrorCode.contextWindow,
                    "context-window",
                    "Request exceeds the model context window",
                    "The request is too large for the selected model.");
        }
        return mapping(
                ErrorCode.validation,
                "bad-request",
                "Provider rejected the request",
                "The provider reported an invalid request.");
    }

    private static Mapping authenticationFailure() {
        return mapping(
                ErrorCode.auth,
                "authentication",
                "Provider authentication failed",
                "The provider rejected the request.");
    }

    private static Mapping modelNotFoundFailure() {
        return mapping(
                ErrorCode.modelNotFound,
                "model-not-found",
                "Provider model not found",
                "The provider does not offer the requested model.");
    }

    private static Mapping classify(Throwable failure) {
        if (failure instanceof InterruptedException) {
            return mapping(
                    ErrorCode.cancelled,
                    "interrupted",
                    "Provider request cancelled",
                    "The provider request was interrupted.");
        }
        if (failure instanceof HttpTimeoutException) {
            return mapping(
                    ErrorCode.timeout,
                    "timeout",
                    "Provider request timed out",
                    "The provider did not respond before the timeout.");
        }
        if (failure instanceof ConnectException
                || failure instanceof UnresolvedAddressException
                || failure instanceof IOException) {
            return mapping(
                    ErrorCode.unreachable,
                    "io-failure",
                    "Provider is unreachable",
                    "The provider could not be reached.");
        }
        return mapping(
                ErrorCode.internal,
                "unexpected-transport-failure",
                "Provider request failed",
                "The provider request could not be completed.");
    }

    private static AppError statusError(
            Mapping mapping, HttpReply reply, ProviderConfig config, @Nullable String modelName) {
        SafeDetails details = SafeDetails.empty().withHttpStatus(reply.status()).withEndpoint(config.baseUrl());
        details = modelName == null ? details : details.withModelName(modelName);
        return AppError.of(mapping.code(), mapping.title(), mapping.message(), details.render(), null);
    }

    private static SafeDetails transportDetails(
            ProviderConfig config, @Nullable String modelName, @Nullable Duration timeout) {
        SafeDetails details = SafeDetails.empty().withEndpoint(config.baseUrl());
        details = modelName == null ? details : details.withModelName(modelName);
        return timeout == null ? details : details.withTimeout(timeout);
    }

    private static Mapping mapping(ErrorCode code, String row, String title, String message) {
        return new Mapping(code, row, title, message);
    }

    /** Captures one D4 row so the chosen code and explanation cannot drift apart.
     *
     * @param code the sole typed classification returned to callers
     * @param row the stable D4 row name written to DEBUG diagnostics
     * @param title a readable, body-independent error heading
     * @param message a readable, body-independent explanation
     */
    private record Mapping(ErrorCode code, String row, String title, String message) {}
}
