package ua.bookloom.llm.http;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;

/**
 * The rows for a prompt that does not fit the model's window. They come last: they are broad, and an engine's own row
 * names its cause more closely.
 */
// Checkstyle reads source before Lombok generates the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ContextRows {

    private static final String TITLE = "Request exceeds the model context window";
    private static final String MESSAGE = "The request is too large for the selected model.";

    // Two lookaheads, so "context ... exceeds" and "request ... exceeds the available context" both match. A deadline,
    // a timeout or a cancellation is an outage worded alike (Go's "context deadline exceeded"), never an overflow.
    private static final Pattern CONTEXT_WORDS = Pattern.compile(
            "\\A(?!.*\\b(?:deadline|timed?\\s*out|cancel))(?=.*\\bcontext\\b)"
                    + "(?=.*\\b(?:exceed(?:s|ed|ing)?|greater\\s+than|too\\s+long|longer\\s+than)\\b)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CONTEXT_CODE = Pattern.compile("context_length_exceeded", Pattern.CASE_INSENSITIVE);
    private static final Pattern N_CTX = Pattern.compile("\\bn_ctx\\b");

    static final List<ErrorRow> ROWS = List.of(
            row("context-words", CONTEXT_WORDS), row("context-code", CONTEXT_CODE), row("context-n-ctx", N_CTX));

    private static ErrorRow row(final String id, final Pattern body) {
        return new ErrorRow(
                id,
                Set.of(),
                body,
                ErrorCode.contextWindow,
                FailureReason.CONTEXT_OVERFLOW,
                RetryRule.NONE,
                TITLE,
                MESSAGE);
    }
}
