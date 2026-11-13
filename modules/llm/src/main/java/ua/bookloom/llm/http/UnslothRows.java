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
 * The rows only an Unsloth server answers with. They are tied to that engine, so another server's 401 or 400 keeps its
 * own meaning, and they come first because they name the cause more closely than the broad rows after them.
 */
// Checkstyle reads source before Lombok generates the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class UnslothRows {

    private static final int HTTP_BAD_REQUEST = 400;
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;
    private static final int WINDOW = 160;
    private static final String NAMES = "response_format|json[ _-]?schema|structured[ _-]?output";
    private static final String VERB =
            "(?:unsupported|not supported|does not support|cannot|can not|not allowed|not implemented|invalid)";

    // A 401 with no body is a key the server did not get; Unsloth words a wrong one as authentication_error.
    static final String KEY_SENTENCE = "Unsloth needs an API key. Create one in Unsloth › Settings › API and put its"
            + " environment-variable name here.";

    static final List<ErrorRow> ROWS = List.of(
            new ErrorRow(
                    "unsloth-key-required",
                    Set.of(ServerEngine.UNSLOTH),
                    Set.of(HTTP_UNAUTHORIZED),
                    Pattern.compile("authentication_error|\\A\\s*\\z", FLAGS),
                    ErrorCode.auth,
                    FailureReason.AUTH,
                    RetryRule.NONE,
                    "Unsloth needs an API key",
                    KEY_SENTENCE),
            new ErrorRow(
                    "unsloth-structured-unavailable",
                    Set.of(ServerEngine.UNSLOTH),
                    Set.of(HTTP_BAD_REQUEST),
                    Pattern.compile(
                            "\\b(?:" + NAMES + ")\\b.{0," + WINDOW + "}" + VERB + "|" + VERB + ".{0," + WINDOW
                                    + "}\\b(?:" + NAMES + ")\\b",
                            FLAGS),
                    ErrorCode.validation,
                    FailureReason.STRUCTURED_UNAVAILABLE,
                    RetryRule.NONE,
                    "Unsloth cannot enforce the answer format",
                    "This model's runtime cannot produce structured answers."));
}
