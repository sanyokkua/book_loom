package ua.bookloom.llm.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.junit.jupiter.params.provider.Arguments;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.llm.http.RetryRule;

/** The recorded Ollama error bodies and what each must be classified as: file, status, code, reason, retry rule. */
// Checkstyle reads source before Lombok generates the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OllamaErrorTable {

    private static final String DIR = "/errors/ollama/";

    static Stream<Arguments> rows() {
        return Stream.of(missingAndRefused(), thinkingAndOptions(), serverSide())
                .flatMap(rows -> rows);
    }

    private static Stream<Arguments> missingAndRefused() {
        return Stream.of(
                row("model-not-pulled", 404, ErrorCode.modelNotFound, FailureReason.MODEL_NOT_PULLED, RetryRule.NONE),
                row(
                        "model-not-pulled-scheduler",
                        404,
                        ErrorCode.modelNotFound,
                        FailureReason.MODEL_NOT_PULLED,
                        RetryRule.NONE),
                row(
                        "model-not-pulled-v1",
                        404,
                        ErrorCode.modelNotFound,
                        FailureReason.MODEL_NOT_PULLED,
                        RetryRule.NONE),
                row("host-forbidden", 403, ErrorCode.auth, FailureReason.HOST_FORBIDDEN, RetryRule.NONE),
                row("cloud-disabled", 403, ErrorCode.auth, FailureReason.CLOUD_DISABLED, RetryRule.NONE),
                row("cloud-signin", 401, ErrorCode.auth, FailureReason.CLOUD_SIGNIN, RetryRule.NONE),
                row("cloud-unreachable", 502, ErrorCode.upstream, FailureReason.CLOUD_UNREACHABLE, RetryRule.BACKOFF));
    }

    private static Stream<Arguments> thinkingAndOptions() {
        return Stream.of(
                row(
                        "thinking-unsupported",
                        400,
                        ErrorCode.validation,
                        FailureReason.THINKING_UNSUPPORTED,
                        RetryRule.NONE),
                row(
                        "thinking-unsupported-v1",
                        400,
                        ErrorCode.validation,
                        FailureReason.THINKING_UNSUPPORTED,
                        RetryRule.NONE),
                row(
                        "think-level-invalid",
                        400,
                        ErrorCode.validation,
                        FailureReason.THINK_LEVEL_INVALID,
                        RetryRule.NONE),
                row(
                        "think-level-invalid-v1",
                        400,
                        ErrorCode.validation,
                        FailureReason.THINK_LEVEL_INVALID,
                        RetryRule.NONE),
                row("option-type", 500, ErrorCode.validation, FailureReason.OPTION_TYPE, RetryRule.NONE),
                row("option-type-duration", 400, ErrorCode.validation, FailureReason.OPTION_TYPE, RetryRule.NONE));
    }

    private static Stream<Arguments> serverSide() {
        return Stream.of(
                row("server-busy", 503, ErrorCode.upstream, FailureReason.SERVER_BUSY, RetryRule.BACKOFF),
                row("runner-crashed", 500, ErrorCode.upstream, FailureReason.RUNNER_CRASHED, RetryRule.SAME),
                row("out-of-memory", 500, ErrorCode.modelUnavailable, FailureReason.OUT_OF_MEMORY, RetryRule.NONE),
                row("repeat-limit", 500, ErrorCode.upstream, FailureReason.REPEAT_LIMIT, RetryRule.NEW_SEED));
    }

    private static Arguments row(
            final String file,
            final int status,
            final ErrorCode code,
            final FailureReason reason,
            final RetryRule retry) {
        return Arguments.of(file, status, code, reason, retry);
    }

    static String fixture(final String file, final int status) {
        try (InputStream in = OllamaErrorTable.class.getResourceAsStream(DIR + file + "." + status + ".json")) {
            return new String(Objects.requireNonNull(in, file).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
