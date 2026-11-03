package ua.bookloom.llm.http;

import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.llm.ServerEngine;

/** What a failed reply means when no catalogue row explains its words: the status alone decides. */
// Checkstyle reads source before Lombok generates the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class StatusMappings {

    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_FORBIDDEN = 403;
    private static final int HTTP_BAD_REQUEST = 400;
    private static final int HTTP_UNPROCESSABLE = 422;
    // A hosted model taken out of service answers 410; it is gone for good, so it reads as a model the provider lacks.
    private static final int HTTP_GONE = 410;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int HTTP_SERVER_ERROR_MIN = 500;
    private static final int HTTP_SERVER_ERROR_MAX = 599;
    // OpenRouter's own timeouts: the request's (408), the gateway's (504) and the hosting company's behind Cloudflare
    // (524). Elsewhere a 504 is a proxy's fault and stays upstream.
    private static final Set<Integer> OPENROUTER_TIMEOUTS = Set.of(408, 504, 524);

    static Mapping of(final int status, final ServerEngine engine) {
        log.debug("HTTP error mapping by status alone status={} engine={}", status, engine);
        if (engine == ServerEngine.OPENROUTER && OPENROUTER_TIMEOUTS.contains(status)) {
            return Mapping.plain(
                    ErrorCode.timeout,
                    "openrouter-timeout",
                    "Provider request timed out",
                    "The provider did not answer in time (HTTP " + status + ").");
        }
        if (status == HTTP_UNAUTHORIZED || status == HTTP_FORBIDDEN) {
            return authenticationFailure(status);
        }
        if (status == HTTP_TOO_MANY_REQUESTS) {
            return Mapping.plain(
                    ErrorCode.rateLimited,
                    "rate-limit",
                    "Provider rate limit reached",
                    "The provider asked the application to slow down.");
        }
        if (status >= HTTP_SERVER_ERROR_MIN && status <= HTTP_SERVER_ERROR_MAX) {
            return Mapping.plain(
                    ErrorCode.upstream,
                    "server-error",
                    "Provider server error",
                    "The AI program had an error (HTTP " + status + ").");
        }
        return status == HTTP_GONE ? modelNotFound() : requestStatus(status);
    }

    static Mapping modelNotFound() {
        return Mapping.plain(
                ErrorCode.modelNotFound,
                "model-not-found",
                "Provider model not found",
                "The provider does not offer the requested model.");
    }

    private static Mapping requestStatus(final int status) {
        if (status == HTTP_BAD_REQUEST || status == HTTP_UNPROCESSABLE) {
            return Mapping.plain(
                    ErrorCode.validation,
                    "bad-request",
                    "Provider rejected the request",
                    "The provider reported an invalid request.");
        }
        return Mapping.plain(
                ErrorCode.internal,
                "unexpected-status",
                "Unexpected provider response",
                "The provider returned an unexpected response.");
    }

    // The server answered: the words say so, so a refused key never reads as "does not answer".
    private static Mapping authenticationFailure(final int status) {
        return Mapping.plain(
                ErrorCode.auth,
                "authentication",
                "The server refused the sign-in",
                "The server answered " + status + (status == HTTP_FORBIDDEN ? " (forbidden)" : " (not authorised)")
                        + ": the sign-in is missing or wrong. Check the sign-in type and the key variable.");
    }
}
