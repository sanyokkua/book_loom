package ua.bookloom.llm.http;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;

/**
 * The rows for the words only LM Studio answers with, one fixture per row under {@code errors/lmstudio}. Order matters:
 * the overflow and grammar rows come before the schema row, whose words ("JSON schema error", "Unable to generate
 * parser") also wrap a grammar failure.
 */
// Checkstyle reads source before Lombok generates the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LmStudioRows {

    private static final int HTTP_NOT_IMPLEMENTED = 501;
    private static final int FLAGS = Pattern.CASE_INSENSITIVE;
    private static final String OVERFLOW_TITLE = "Request exceeds the model context window";
    private static final String OVERFLOW_MESSAGE =
            "The prompt did not fit the loaded context. Reload the model with a larger context.";

    static final List<ErrorRow> ROWS = List.of(
            any(
                    "lmstudio-terminated",
                    "\"(?:error|message)\"\\s*:\\s*\"terminated\\.?\"",
                    ErrorCode.upstream,
                    FailureReason.TERMINATED,
                    RetryRule.SAME,
                    "Provider stopped answering",
                    "The server closed the connection while it was answering."),
            any(
                    "lmstudio-no-model-loaded",
                    "\\bno\\s+models?\\s+(?:are\\s+|is\\s+)?loaded\\b",
                    ErrorCode.modelUnavailable,
                    FailureReason.NO_MODEL_LOADED,
                    RetryRule.AUTO_WAIT,
                    "Provider has no model loaded",
                    "The provider has no model loaded to answer the request."),
            // A local server that dropped the model (idle unload, JIT eviction) or a model "not loaded": loading it
            // fixes it, so it is never a bad request.
            any(
                    "lmstudio-model-unloaded",
                    "\\bmodel\\s+(?:is\\s+|was\\s+|has\\s+been\\s+)?unloaded\\b"
                            + "|\\bmodel\\b[^\"]{0,256}\\bnot\\s+(?:currently\\s+)?loaded\\b",
                    ErrorCode.modelUnavailable,
                    FailureReason.MODEL_UNLOADED,
                    RetryRule.AUTO_WAIT,
                    "Provider model is not loaded",
                    "The provider has no model loaded to answer the request."),
            overflow("lmstudio-context-exceeded", "\\bcontext\\s+size\\s+has\\s+been\\s+exceeded\\b"),
            overflow(
                    "lmstudio-tokens-to-keep",
                    "tokens\\s+to\\s+keep[^\"]{0,200}greater\\s+than\\s+the\\s+context\\s+length"),
            any(
                    "lmstudio-grammar-too-large",
                    "failed\\s+to\\s+parse\\s+grammar",
                    ErrorCode.validation,
                    FailureReason.GRAMMAR_TOO_LARGE,
                    RetryRule.NONE,
                    "Provider could not use the answer format",
                    "The structured-answer grammar is too large for the server."),
            new ErrorRow(
                    "lmstudio-structured-unavailable",
                    Set.of(HTTP_NOT_IMPLEMENTED),
                    Pattern.compile("structured\\s+output\\s+is\\s+unavailable", FLAGS),
                    ErrorCode.validation,
                    FailureReason.STRUCTURED_UNAVAILABLE,
                    RetryRule.NONE,
                    "Provider has no structured output",
                    "This model's runtime cannot produce structured answers."),
            // A template with no system role (Gemma 2 and 3 and their tunes) says so inside its Jinja exception; the
            // request is resent with the system text folded into the first user message.
            any(
                    "system-role-unsupported",
                    "system\\s+role\\s+(?:is\\s+)?not\\s+supported",
                    ErrorCode.validation,
                    FailureReason.SYSTEM_ROLE_UNSUPPORTED,
                    RetryRule.NONE,
                    "Provider refused the system message",
                    "The model's chat template has no system role."),
            any(
                    "lmstudio-template-rejected",
                    "Jinja\\s+Exception",
                    ErrorCode.validation,
                    FailureReason.TEMPLATE_REJECTED,
                    RetryRule.NONE,
                    "Provider rejected the message layout",
                    "The model's chat template refused the way the messages were arranged."),
            any(
                    "lmstudio-engine-unreachable",
                    "Engine\\s+protocol\\s+predict\\s+request\\s+failed:\\s*fetch\\s+failed",
                    ErrorCode.upstream,
                    FailureReason.ENGINE_UNREACHABLE,
                    RetryRule.SAME,
                    "Provider engine did not answer",
                    "The server could not reach its own model engine."),
            any(
                    "lmstudio-schema-invalid",
                    "JSON\\s+schema\\s+error|Invalid\\s+structured\\s+output\\s+configuration"
                            + "|Pointer\\s+'[^']{0,200}'\\s+does\\s+not\\s+exist|Unable\\s+to\\s+generate\\s+parser"
                            + "|cannot\\s+resolve\\s+\\$ref",
                    ErrorCode.validation,
                    FailureReason.SCHEMA_INVALID,
                    RetryRule.NONE,
                    "Provider could not use the answer format",
                    "The server cannot use the structured-answer schema it was sent."));

    private static ErrorRow overflow(final String id, final String body) {
        return any(
                id,
                body,
                ErrorCode.contextWindow,
                FailureReason.CONTEXT_OVERFLOW,
                RetryRule.NONE,
                OVERFLOW_TITLE,
                OVERFLOW_MESSAGE);
    }

    private static ErrorRow any(
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
