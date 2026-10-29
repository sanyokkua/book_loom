package ua.bookloom.llm.http;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;

/**
 * The rows for the words only Ollama answers with, on its native route and on {@code /v1} alike. They match by text,
 * so until the engine mask of group 5 an Ollama row may also catch the same words from another engine.
 */
// Checkstyle reads source before Lombok generates the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OllamaRows {

    /**
     * Ollama's "model … not found" inside an error or message field, quiet enough that a 200 answer which merely
     * quotes those words in its text is no error.
     */
    static final Pattern NOT_PULLED = Pattern.compile(
            "\"(?:error|message)\"\\s*:\\s*\"(?:[^\"\\\\]|\\\\.){0,512}?\\bmodel\\b(?:[^\"\\\\]|\\\\.){0,256}?"
                    + "\\bnot\\s+found\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Set<Integer> BAD_REQUEST = Set.of(400);
    private static final int FORBIDDEN = 403;
    private static final int BAD_GATEWAY = 502;
    private static final int FLAGS = Pattern.CASE_INSENSITIVE;

    // Each row says who is asked: the person for host, sign-in, cloud, memory and model; nobody for the rest (a
    // BookLoom fault for option-type, a passing fault for busy, repeat and crash).
    static final List<ErrorRow> ROWS = List.of(
            // Ollama's Host guard answers 403 with no body at all; the engine is not known until group 5.
            new ErrorRow(
                    "ollama-host-forbidden",
                    Set.of(FORBIDDEN),
                    Pattern.compile("\\A\\s*\\z"),
                    ErrorCode.auth,
                    FailureReason.HOST_FORBIDDEN,
                    RetryRule.NONE,
                    "Ollama refused this address",
                    "Ollama refused this address. Use 127.0.0.1, or let Ollama listen on the network."),
            row(
                    "ollama-cloud-signin",
                    "\"signin_url\"",
                    ErrorCode.auth,
                    FailureReason.CLOUD_SIGNIN,
                    RetryRule.NONE,
                    "Sign in to Ollama",
                    "Sign in to Ollama to use this cloud model, then try again."),
            row(
                    "ollama-cloud-disabled",
                    "ollama\\s+cloud\\s+is\\s+disabled",
                    ErrorCode.auth,
                    FailureReason.CLOUD_DISABLED,
                    RetryRule.NONE,
                    "Ollama cloud models are off",
                    "Ollama cloud models are turned off on this computer. Turn them on in Ollama, or choose a local"
                            + " model."),
            new ErrorRow(
                    "ollama-cloud-unreachable",
                    Set.of(BAD_GATEWAY),
                    Pattern.compile("\\b(?:cloud|remote\\s+model|ollama\\.com|bad\\s+gateway)\\b", FLAGS),
                    ErrorCode.upstream,
                    FailureReason.CLOUD_UNREACHABLE,
                    RetryRule.BACKOFF,
                    "Ollama cloud is unreachable",
                    "Ollama could not reach its cloud service. Check the internet connection and try again."),
            new ErrorRow(
                    "ollama-model-not-pulled",
                    Set.of(),
                    NOT_PULLED,
                    ErrorCode.modelNotFound,
                    FailureReason.MODEL_NOT_PULLED,
                    RetryRule.NONE,
                    "Model is not installed",
                    "This model is not installed in Ollama. Pull it (`ollama pull {model}`) or pick another."),
            new ErrorRow(
                    "ollama-thinking-unsupported",
                    BAD_REQUEST,
                    Pattern.compile("\\bdoes\\s+not\\s+support\\s+think(?:ing)?\\b", FLAGS),
                    ErrorCode.validation,
                    FailureReason.THINKING_UNSUPPORTED,
                    RetryRule.NONE,
                    "Model cannot think",
                    "This model has no thinking mode, so the thinking setting was refused."),
            new ErrorRow(
                    "ollama-think-level-invalid",
                    BAD_REQUEST,
                    Pattern.compile(
                            "\\binvalid\\s+(?:think|reasoning)\\s+value\\b|\\bthink\\s+must\\s+be\\s+a\\s+boolean\\b",
                            FLAGS),
                    ErrorCode.validation,
                    FailureReason.THINK_LEVEL_INVALID,
                    RetryRule.NONE,
                    "Thinking level refused",
                    "The model does not accept this thinking level."),
            row(
                    "ollama-option-type",
                    "\\boption\\s+\\\\?\"[^\"\\\\]+\\\\?\"\\s+must\\s+be\\s+of\\s+type\\b|\\bmissing\\s+unit\\s+in"
                            + "\\s+duration\\b",
                    ErrorCode.validation,
                    FailureReason.OPTION_TYPE,
                    RetryRule.NONE,
                    "Ollama rejected a setting",
                    "BookLoom sent Ollama a setting in the wrong form. This is a fault in BookLoom; see the log."),
            row(
                    "ollama-server-busy",
                    "\\bserver\\s+busy\\b",
                    ErrorCode.upstream,
                    FailureReason.SERVER_BUSY,
                    RetryRule.BACKOFF,
                    "Ollama is busy",
                    "Ollama has too many requests waiting. BookLoom waits and tries again."),
            row(
                    "ollama-out-of-memory",
                    "\\bmodel\\s+requires\\s+(?<needs>\\d+(?:\\.\\d+)?)\\s*GiB\\s+but\\s+only\\s+(?<free>\\d+(?:\\.\\d+)?)"
                            + "\\s*GiB\\s+are\\s+available",
                    ErrorCode.modelUnavailable,
                    FailureReason.OUT_OF_MEMORY,
                    RetryRule.NONE,
                    "Not enough memory for this model",
                    "Not enough memory to load this model (needs {needs} GiB, {free} GiB free). Close other apps or"
                            + " choose a smaller build."),
            row(
                    "ollama-repeat-limit",
                    "\\btoken\\s+repeat\\s+limit\\b",
                    ErrorCode.upstream,
                    FailureReason.REPEAT_LIMIT,
                    RetryRule.NEW_SEED,
                    "The model repeated itself",
                    "The model got stuck repeating itself and Ollama stopped it."),
            row(
                    "ollama-runner-crashed",
                    "\\bmodel\\s+runner\\s+has\\s+unexpectedly\\s+stopped\\b",
                    ErrorCode.upstream,
                    FailureReason.RUNNER_CRASHED,
                    RetryRule.SAME,
                    "Ollama's model process stopped",
                    "Ollama's model process stopped unexpectedly. It may have run out of resources."));

    private static ErrorRow row(
            final String id,
            final String body,
            final ErrorCode code,
            final FailureReason reason,
            final RetryRule retry,
            final String title,
            final String message) {
        return new ErrorRow(id, Set.of(), Pattern.compile(body, FLAGS), code, reason, retry, title, message);
    }
}
