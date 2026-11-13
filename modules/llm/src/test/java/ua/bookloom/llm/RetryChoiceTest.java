package ua.bookloom.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.llm.RetryChoice.Resend;
import ua.bookloom.llm.retry.RetryPolicy;

class RetryChoiceTest {

    private static final RetryPolicy POLICY = new RetryPolicy(
            Clock.fixed(Instant.parse("2026-10-29T12:00:00Z"), ZoneOffset.UTC), () -> 0.5, ignored -> {});

    // A timeout keeps its ladder; a row's own rule decides next; a failure no row explains keeps the code's rule.
    @ParameterizedTest
    @CsvSource(
            nullValues = "-",
            value = {
                "timeout,          -,                  1, 0, -,                  VARIED_AFTER_TIMEOUT",
                "timeout,          -,                  2, 1, -,                  NONE",
                "upstream,         TERMINATED,         1, 0, -,                  SAME",
                "upstream,         TERMINATED,         2, 0, TERMINATED,         NONE",
                "upstream,         RUNNER_CRASHED,     1, 0, -,                  SAME",
                "upstream,         ENGINE_UNREACHABLE, 1, 0, -,                  SAME",
                "upstream,         REPEAT_LIMIT,       1, 0, -,                  NEW_SEED",
                "upstream,         REPEAT_LIMIT,       2, 0, REPEAT_LIMIT,       NONE",
                "upstream,         REPEAT_LIMIT,       3, 0, -,                  NONE",
                "modelUnavailable, OUT_OF_MEMORY,      1, 0, -,                  NONE",
                "modelUnavailable, MODEL_LOADING,      1, 0, -,                  NONE",
                "modelUnavailable, NO_MODEL_LOADED,    1, 0, -,                  NONE",
                "modelUnavailable, MODEL_UNLOADED,     1, 0, -,                  NONE",
                "upstream,         SERVER_BUSY,        1, 0, -,                  BACKOFF",
                "upstream,         SERVER_BUSY,        2, 0, -,                  BACKOFF",
                "upstream,         SERVER_BUSY,        3, 0, -,                  NONE",
                "upstream,         CLOUD_UNREACHABLE,  2, 0, -,                  BACKOFF",
                "upstream,         -,                  1, 0, -,                  BACKOFF",
                "upstream,         UNCLASSIFIED,       2, 0, -,                  BACKOFF",
                "upstream,         UNCLASSIFIED,       3, 0, -,                  NONE",
                "auth,             -,                  1, 0, -,                  NONE",
                "validation,       OPTION_TYPE,        1, 0, -,                  NONE",
                "contextWindow,    CONTEXT_OVERFLOW,   1, 0, -,                  NONE"
            })
    void decide_reasonAndAttempt_matchesTheTable(
            final ErrorCode code,
            @Nullable final FailureReason reason,
            final int failedAttempt,
            final int timedOutBefore,
            @Nullable final FailureReason resent,
            final Resend expected) {
        final RetryChoice.Decision decision =
                RetryChoice.decide(error(code, reason), failedAttempt, timedOutBefore, resentOnce(resent), POLICY);

        assertThat(decision.resend()).isEqualTo(expected);
    }

    private static AppError error(final ErrorCode code, @Nullable final FailureReason reason) {
        final String details =
                reason == null ? null : SafeDetails.empty().withReason(reason).render();
        return AppError.of(code, "Failed", "The provider failed.", details, null);
    }

    private static Set<FailureReason> resentOnce(@Nullable final FailureReason resent) {
        return resent == null ? EnumSet.noneOf(FailureReason.class) : EnumSet.of(resent);
    }
}
