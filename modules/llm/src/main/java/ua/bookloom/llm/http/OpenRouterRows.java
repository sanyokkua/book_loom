package ua.bookloom.llm.http;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.llm.ServerEngine;

/**
 * The rows only OpenRouter answers with (design D21). They are tied to that engine, so another server's 402 or 403
 * keeps its own meaning. An outage OpenRouter reports for a hosting company carries its {@code error_type} whatever
 * the status, even inside a 200; the credit refusal is read from the status, because a 429 from a shared upstream pool
 * also names a {@code limit_source}.
 */
// Checkstyle reads source before Lombok generates the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OpenRouterRows {

    private static final int HTTP_PAYMENT_REQUIRED = 402;
    private static final int HTTP_FORBIDDEN = 403;
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;
    private static final Set<ServerEngine> OPENROUTER = Set.of(ServerEngine.OPENROUTER);
    // Found in every body, so a row tied to a status decides by the status alone.
    private static final Pattern ANY_BODY = Pattern.compile("\\A");
    // "No endpoints found matching your data policy" is another refusal; only the parameters one is this row's.
    private static final Pattern PARAMETERS = Pattern.compile("\\bparameters?\\b|require_parameters", FLAGS);

    static final List<ErrorRow> ROWS = List.of(
            new ErrorRow(
                    "openrouter-provider-unavailable",
                    OPENROUTER,
                    Set.of(),
                    Pattern.compile("\"error_type\"\\s*:\\s*\"provider_unavailable\"", FLAGS),
                    ErrorCode.upstream,
                    FailureReason.PROVIDER_UNAVAILABLE,
                    RetryRule.BACKOFF,
                    "The model's provider is unavailable",
                    "OpenRouter could not reach a provider for this model. The call is sent again after a pause."),
            new ErrorRow(
                    "openrouter-no-endpoint-for-parameters",
                    OPENROUTER,
                    Set.of(),
                    Pattern.compile("\\bno\\s+endpoints?\\b", FLAGS),
                    text -> PARAMETERS.matcher(text).find(),
                    ErrorCode.validation,
                    FailureReason.NO_ENDPOINT_FOR_PARAMETERS,
                    RetryRule.NONE,
                    "No provider honours the request",
                    "No provider of this model supports structured answers or turning reasoning off."),
            new ErrorRow(
                    "openrouter-credits",
                    OPENROUTER,
                    Set.of(HTTP_PAYMENT_REQUIRED),
                    ANY_BODY,
                    ErrorCode.auth,
                    FailureReason.CREDITS,
                    RetryRule.NONE,
                    "OpenRouter refused the call",
                    "OpenRouter refused the call: no credits or the key's limit is reached."),
            new ErrorRow(
                    "openrouter-blocked",
                    OPENROUTER,
                    Set.of(HTTP_FORBIDDEN),
                    Pattern.compile("content[ _-]?policy|moderation|\\bflagged\\b", FLAGS),
                    ErrorCode.validation,
                    FailureReason.BLOCKED,
                    RetryRule.NONE,
                    "The provider refused this passage",
                    "The provider refused this passage."));
}
