package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.ModelCallFinished;

/** Proves the per-kind totals keep each kind apart and treat a missing figure as zero rather than guessing. */
class CallKindTotalsTest {

    private static ModelCallFinished answered(CallKind kind, long wallMillis, TokenUsage usage) {
        return new ModelCallFinished(
                "s1", kind, Duration.ofMillis(wallMillis), usage, 10, false, List.of("s1"), 1, null);
    }

    // Two drafts and a judge: each kind sums its own tokens and times and the other kind is untouched.
    @Test
    void record_twoKinds_sumsEachKindSeparately() {
        final CallKindTotals totals = new CallKindTotals();
        totals.record(answered(
                CallKind.DRAFT, 3000, new TokenUsage(800, 90, Duration.ofMillis(2500), Duration.ofMillis(400), 600)));
        totals.record(answered(
                CallKind.DRAFT, 2000, new TokenUsage(700, 60, Duration.ofMillis(1500), Duration.ofMillis(300), 500)));
        totals.record(answered(CallKind.REVIEW, 1000, new TokenUsage(500, 20, Duration.ofMillis(800))));

        assertThat(totals.totals().get(CallKind.DRAFT))
                .isEqualTo(new CallKindTotals.Totals(
                        2,
                        0,
                        1500,
                        150,
                        Duration.ofMillis(5000),
                        Duration.ofMillis(700),
                        Duration.ofMillis(4000),
                        1100));
        assertThat(totals.totals().get(CallKind.REVIEW))
                .isEqualTo(new CallKindTotals.Totals(
                        1, 0, 500, 20, Duration.ofMillis(1000), Duration.ZERO, Duration.ofMillis(800), 0));
    }

    // A failed attempt counts as an attempt and a failure but adds no tokens; a reply with no usage adds only its time.
    @Test
    void record_failedAndUnreportedAttempts_countedWithoutTokens() {
        final CallKindTotals totals = new CallKindTotals();
        totals.record(new ModelCallFinished(
                "s1", CallKind.DRAFT, Duration.ofSeconds(60), null, 0, false, List.of("s1"), 1, ErrorCode.timeout));
        totals.record(new ModelCallFinished("s1", CallKind.DRAFT, Duration.ofSeconds(4), null, 10, false));

        assertThat(totals.totals().get(CallKind.DRAFT))
                .isEqualTo(
                        new CallKindTotals.Totals(2, 1, 0, 0, Duration.ofSeconds(64), Duration.ZERO, Duration.ZERO, 0));
    }
}
