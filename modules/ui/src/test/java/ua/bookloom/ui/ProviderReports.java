package ua.bookloom.ui;

import java.time.Duration;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationExchange;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;

/** Verification reports for tests that need a finished check with the request each stage kept. */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProviderReports {

    private static final String HEADERS = "Authorization: Bearer ••••\nContent-Type: application/json";

    /** A report in which all three stages passed and each kept the request it sent, the way the real verifier does. */
    public static Result<VerificationReport> allPassedWithExchanges() {
        return Result.ok(new VerificationReport(List.of(connection(), models(), inference())));
    }

    /**
     * A report in which all three stages passed and the inference reply carried the given prompt timings.
     *
     * @param firstTokenMs the prompt-reading time in milliseconds, or null when the route reports none
     * @param promptTokens the prompt tokens counted
     * @param cachedPrompt the prompt tokens served from the cache, or null when not reported
     * @return the report
     */
    public static Result<VerificationReport> allPassedWithTimings(
            final @Nullable Long firstTokenMs, final Integer promptTokens, final @Nullable Integer cachedPrompt) {
        final StageOutcome timed = new StageOutcome(
                VerificationStage.INFERENCE,
                StageStatus.PASSED,
                null,
                null,
                Duration.ofMillis(1200),
                null,
                new VerificationExchange(
                        "POST",
                        "http://127.0.0.1:11434/v1/chat/completions",
                        "{}",
                        "OK",
                        200,
                        firstTokenMs,
                        42.5,
                        true,
                        HEADERS,
                        promptTokens,
                        3,
                        "{}",
                        "OK",
                        null,
                        cachedPrompt,
                        List.of()));
        return Result.ok(new VerificationReport(List.of(connection(), models(), timed)));
    }

    /**
     * A report of the Advanced features test: the three stages passed, then the optional stage with its note.
     *
     * @param note the verdicts in the verifier's machine words, such as {@code schema=honoured; thinking=think}
     * @return the four stages, the last one passed without an exchange of its own
     */
    public static Result<VerificationReport> advancedPassed(final String note) {
        final StageOutcome advanced = new StageOutcome(
                VerificationStage.ADVANCED, StageStatus.PASSED, null, note, Duration.ofMillis(3400), null, null);
        return Result.ok(new VerificationReport(List.of(connection(), models(), inference(), advanced)));
    }

    /** A report of Test connection alone that nothing answered, the way a closed local program fails it. */
    public static Result<VerificationReport> connectionNotAnswered() {
        return Result.ok(new VerificationReport(List.of(new StageOutcome(
                VerificationStage.CONNECTION,
                StageStatus.FAILED,
                AppError.of(ErrorCode.unreachable, "Unreachable", "The server did not answer."),
                null,
                Duration.ofMillis(1400),
                null,
                new VerificationExchange("GET", "http://127.0.0.1:11434/", "", "", 0, null, null, false, HEADERS)))));
    }

    private static StageOutcome connection() {
        return new StageOutcome(
                VerificationStage.CONNECTION,
                StageStatus.PASSED,
                null,
                null,
                Duration.ofMillis(41),
                null,
                new VerificationExchange("GET", "http://127.0.0.1:11434/", "", "", 200, null, null, false, HEADERS));
    }

    private static StageOutcome models() {
        return new StageOutcome(
                VerificationStage.MODELS,
                StageStatus.PASSED,
                null,
                null,
                Duration.ofMillis(5),
                3,
                new VerificationExchange(
                        "GET",
                        "http://127.0.0.1:11434/api/tags",
                        "",
                        "gemma3:12b\nqwen3:8b\nllama3:8b",
                        200,
                        null,
                        null,
                        false,
                        HEADERS));
    }

    private static StageOutcome inference() {
        return new StageOutcome(
                VerificationStage.INFERENCE,
                StageStatus.PASSED,
                null,
                null,
                Duration.ofMillis(1200),
                null,
                new VerificationExchange(
                        "POST",
                        "http://127.0.0.1:11434/api/chat",
                        "{\"model\":\"gemma3:12b\"}",
                        "OK",
                        200,
                        310L,
                        42.5,
                        true,
                        HEADERS,
                        12,
                        3));
    }
}
