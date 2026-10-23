package ua.bookloom.ui;

import java.time.Duration;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
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
