package ua.bookloom.llm.http;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;

/**
 * The rows for the words only llama-server answers with, one fixture per row under {@code errors/llamacpp}. Its
 * schema-parser failure is read by the LM Studio schema row, whose words it shares.
 */
// Checkstyle reads source before Lombok generates the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LlamaServerRows {

    private static final int HTTP_UNAVAILABLE = 503;

    static final List<ErrorRow> ROWS = List.of(
            new ErrorRow(
                    "llama-exceed-context",
                    Set.of(),
                    Pattern.compile("exceed_context_size_error", Pattern.CASE_INSENSITIVE),
                    ErrorCode.contextWindow,
                    FailureReason.CONTEXT_OVERFLOW,
                    RetryRule.NONE,
                    "Request exceeds the model context window",
                    "The request is too large for the selected model."),
            new ErrorRow(
                    "llama-model-loading",
                    Set.of(HTTP_UNAVAILABLE),
                    Pattern.compile("\\bLoading\\s+model\\b", Pattern.CASE_INSENSITIVE),
                    ErrorCode.modelUnavailable,
                    FailureReason.MODEL_LOADING,
                    RetryRule.AUTO_WAIT,
                    "Provider is still loading the model",
                    "The server is loading the model and will answer when it is ready."),
            new ErrorRow(
                    "llama-schema-not-applied",
                    Set.of(),
                    Pattern.compile(
                            "\\bunparsed\\s+peg-[\\w-]+|does\\s+not\\s+match\\s+the\\s+expected\\s+peg-native\\s+format",
                            Pattern.CASE_INSENSITIVE),
                    ErrorCode.validation,
                    FailureReason.SCHEMA_NOT_APPLIED,
                    RetryRule.NONE,
                    "Provider reply did not follow the answer format",
                    "The server could not read the model's reply in the structured format that was asked for."));
}
