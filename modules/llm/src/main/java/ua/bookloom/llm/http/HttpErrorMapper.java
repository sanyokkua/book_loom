package ua.bookloom.llm.http;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.Result;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;

/**
 * Classifies a provider's failure: interruption, timeout and I/O first; then the catalogue's rows by the meaning of the
 * body's words, whatever the status; then the status alone. A response body decides the classification but never
 * enters an error, and reaches the log at TRACE only.
 */
// Checkstyle reads source before Lombok generates the private constructor; match the repository's documented escape.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HttpErrorMapper {

    // An SSE failure frame: LM Studio streams a failure as HTTP 200 followed by "event: error".
    private static final Pattern ERROR_EVENT = Pattern.compile("(?m)^\\s*event:\\s*error\\s*$");
    // A body that is nothing but an error object: LM Studio's "Unexpected endpoint or method" for a path it does not
    // serve, which it sends with HTTP 200.
    private static final Pattern ERROR_ONLY_BODY = Pattern.compile("\\A\\s*\\{\\s*\"error\"\\s*:\\s*[\"{]");
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_FORBIDDEN = 403;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_SUCCESS_MIN = 200;
    private static final int HTTP_SUCCESS_MAX = 299;
    private static final int HTTP_SERVER_ERROR_MIN = 500;
    private static final int HTTP_SERVER_ERROR_MAX = 599;
    private static final int MAX_TRACED_BODY = 2000;
    private static final String CONTROL_OR_SPACE = "[\\p{Cntrl}\\s]+";

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
        if (isErrorOnlySuccess(reply, config, purpose)) {
            return Result.err(errorOnlySuccess(reply, config, purpose, modelName));
        }
        if (isProbeReachable(purpose, reply.status())) {
            return successfulReply(reply, config, purpose, "probe-reachable");
        }
        if (isSuccessful(reply.status()) && !isOllamaModelNotFound(reply, config)) {
            return successfulReply(reply, config, purpose, "successful-response");
        }
        return failedReply(reply, config, purpose, modelName);
    }

    private static Result<HttpReply> failedReply(
            HttpReply reply, ProviderConfig config, CallPurpose purpose, @Nullable String modelName) {
        final Mapping mapping = classify(reply, config, purpose, modelName);
        final AppError error = statusError(mapping, reply.status(), reply.body(), config, modelName);
        log.debug(
                "HTTP error mapping selected row={} purpose={} code={} reason={} host={} status={}",
                mapping.row(),
                purpose,
                error.code(),
                mapping.reason().token(),
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

    /**
     * Classifies the error line of a streamed reply by its inner text, through the same rows as a failed status.
     *
     * @param config the endpoint that answered
     * @param modelName the model the request named, if known
     * @param errorText the line's {@code error} text; read for its meaning, kept only at TRACE
     * @return the failure: the matching row's, or {@code upstream} with {@code unclassified} when no row explains it
     */
    public static AppError mapStreamedError(ProviderConfig config, @Nullable String modelName, String errorText) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(errorText, "errorText");
        log.debug(
                "Streamed error mapping host={} textLength={} modelPresent={}",
                config.baseUrl().getHost(),
                errorText.length(),
                modelName != null);
        log.trace("Streamed error text={}", errorText);
        final Mapping mapping = ErrorRows.matchText(errorText, config.effectiveEngine())
                .map(row -> Mapping.of(row, errorText, modelName))
                .orElseGet(() -> Mapping.plain(
                        ErrorCode.upstream,
                        "stream-error",
                        "Provider server error",
                        "Ollama stopped the reply with an error."));
        return statusError(mapping, null, errorText, config, modelName);
    }

    /**
     * What trying again can do for a failure with this reason.
     *
     * @param reason the failure's reason token
     * @return the retry rule of the first catalogue row with that reason, or empty if no row has it
     */
    public static Optional<RetryRule> retryRule(FailureReason reason) {
        Objects.requireNonNull(reason, "reason");
        return ErrorRows.ORDERED.stream()
                .filter(row -> row.reason() == reason)
                .map(ErrorRow::retry)
                .findFirst();
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

    // An Ollama chat reply is a stream whose error lines its reader handles, and Ollama's own "model not found" keeps
    // its row; every other success that is only an error object means the path is not served.
    private static boolean isErrorOnlySuccess(HttpReply reply, ProviderConfig config, CallPurpose purpose) {
        final boolean streamed = purpose == CallPurpose.CHAT && config.kind() == ProviderKind.OLLAMA;
        return !streamed
                && isSuccessful(reply.status())
                && (ERROR_ONLY_BODY.matcher(reply.body()).find()
                        || ERROR_EVENT.matcher(reply.body()).find())
                && !isOllamaModelNotFound(reply, config);
    }

    // An error object a row explains (LM Studio sends some failures with HTTP 200) keeps the row's meaning; any other
    // means the path is not served.
    private static AppError errorOnlySuccess(
            HttpReply reply, ProviderConfig config, CallPurpose purpose, @Nullable String modelName) {
        traceBody(reply);
        final Mapping mapping = ErrorRows.match(reply.status(), reply.body(), config.effectiveEngine())
                .map(row -> Mapping.of(row, reply.body(), modelName))
                .orElseGet(() -> unknownSuccessError(reply, config));
        final AppError error = statusError(mapping, reply.status(), reply.body(), config, modelName);
        log.warn(
                "Provider answered a success holding only an error purpose={} host={} status={} code={} reason={}",
                purpose,
                config.baseUrl().getHost(),
                reply.status(),
                error.code(),
                mapping.reason().token());
        return error;
    }

    // An SSE error event is a failure of the call, not a wrong path; only a bare error object means the path is not
    // served.
    private static Mapping unknownSuccessError(HttpReply reply, ProviderConfig config) {
        if (ERROR_EVENT.matcher(reply.body()).find()) {
            log.debug(
                    "Success holds an unknown error event host={}",
                    config.baseUrl().getHost());
            return new Mapping(
                    ErrorCode.upstream,
                    "error-event",
                    "Provider reported an error",
                    "The AI program reported an error while answering.",
                    FailureReason.UNCLASSIFIED);
        }
        return wrongPath(config);
    }

    private static Mapping wrongPath(ProviderConfig config) {
        final String path =
                config.baseUrl().getPath() == null ? "" : config.baseUrl().getPath();
        final boolean missingV1 = config.kind() == ProviderKind.OPENAI_COMPATIBLE && !path.matches(".*/v1/?");
        log.debug(
                "Wrong-path answer worded host={} kind={} missingV1={}",
                config.baseUrl().getHost(),
                config.kind(),
                missingV1);
        return new Mapping(
                ErrorCode.validation,
                "error-only-success",
                "The server does not serve this path",
                "The server answered, but it does not serve this path: "
                        + (missingV1
                                ? "the address probably needs /v1 at the end."
                                : "check the address and the kind of server."),
                FailureReason.WRONG_PATH);
    }

    private static boolean isProbeReachable(CallPurpose purpose, int status) {
        return purpose == CallPurpose.PROBE
                && status != HTTP_UNAUTHORIZED
                && status != HTTP_FORBIDDEN
                && (status < HTTP_SERVER_ERROR_MIN || status > HTTP_SERVER_ERROR_MAX);
    }

    private static boolean isSuccessful(int status) {
        return status >= HTTP_SUCCESS_MIN && status <= HTTP_SUCCESS_MAX;
    }

    private static Mapping classify(
            HttpReply reply, ProviderConfig config, CallPurpose purpose, @Nullable String modelName) {
        traceBody(reply);
        final int status = reply.status();
        // A refused sign-in is the news even when its body also says a model is missing.
        final Optional<ErrorRow> row = ErrorRows.match(status, reply.body(), config.effectiveEngine())
                .filter(found -> !(isAuthStatus(status) && found.code() == ErrorCode.modelNotFound));
        if (row.isPresent()) {
            return Mapping.of(row.get(), reply.body(), modelName);
        }
        if (!isAuthStatus(status) && purpose == CallPurpose.CHAT && status == HTTP_NOT_FOUND) {
            log.debug("HTTP error mapping legacy guard=model-not-found purpose={} status={}", purpose, status);
            return StatusMappings.modelNotFound();
        }
        return StatusMappings.of(status);
    }

    private static boolean isAuthStatus(int status) {
        return status == HTTP_UNAUTHORIZED || status == HTTP_FORBIDDEN;
    }

    private static boolean isOllamaModelNotFound(HttpReply reply, ProviderConfig config) {
        return config.kind() == ProviderKind.OLLAMA
                && OllamaRows.NOT_PULLED.matcher(reply.body()).find();
    }

    // The provider's own sentence explains a failure no row knows, so it is kept, but only at TRACE: it can quote the
    // book's text.
    private static void traceBody(HttpReply reply) {
        if (log.isTraceEnabled()) {
            final String flat = reply.body().replaceAll(CONTROL_OR_SPACE, " ").trim();
            log.trace(
                    "Provider failure body status={} body={}",
                    reply.status(),
                    flat.length() <= MAX_TRACED_BODY ? flat : flat.substring(0, MAX_TRACED_BODY) + "…");
        }
    }

    private static Mapping classify(Throwable failure) {
        if (failure instanceof InterruptedException) {
            return Mapping.plain(
                    ErrorCode.cancelled,
                    "interrupted",
                    "Provider request cancelled",
                    "The provider request was interrupted.");
        }
        if (failure instanceof HttpTimeoutException) {
            return Mapping.plain(
                    ErrorCode.timeout,
                    "timeout",
                    "Provider request timed out",
                    "The provider did not respond before the timeout.");
        }
        if (failure instanceof ConnectException
                || failure instanceof UnresolvedAddressException
                || failure instanceof IOException) {
            return Mapping.plain(
                    ErrorCode.unreachable,
                    "io-failure",
                    "Provider is unreachable",
                    "The provider could not be reached.");
        }
        return Mapping.plain(
                ErrorCode.internal,
                "unexpected-transport-failure",
                "Provider request failed",
                "The provider request could not be completed.");
    }

    private static AppError statusError(
            Mapping mapping, @Nullable Integer status, String body, ProviderConfig config, @Nullable String modelName) {
        SafeDetails details = SafeDetails.empty().withReason(mapping.reason()).withEndpoint(config.baseUrl());
        details = status == null ? details : details.withHttpStatus(status);
        details = modelName == null ? details : details.withModelName(modelName);
        if (mapping.reason() == FailureReason.CONTEXT_OVERFLOW) {
            final SafeDetails overflow = details;
            details = TokenCounts.in(body)
                    .map(counts -> overflow.withTokenCounts(counts.prompt(), counts.window()))
                    .orElse(details);
        }
        return AppError.of(mapping.code(), mapping.title(), mapping.message(), details.render(), null);
    }

    private static SafeDetails transportDetails(
            ProviderConfig config, @Nullable String modelName, @Nullable Duration timeout) {
        SafeDetails details = SafeDetails.empty().withEndpoint(config.baseUrl());
        details = modelName == null ? details : details.withModelName(modelName);
        return timeout == null ? details : details.withTimeout(timeout);
    }
}
