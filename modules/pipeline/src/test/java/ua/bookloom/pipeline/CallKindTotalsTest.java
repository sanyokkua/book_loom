package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.llm.CallRecord;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.CallKind;

/**
 * Proves the per-kind totals keep each kind apart, sum only the figures the calls reported and average each figure
 * over the calls that reported it, so a server that reports no cached tokens does not look as if it cached none.
 */
class CallKindTotalsTest {

    private static CallRecord answered(final CallKind kind, final long wallMillis, final TokenUsage usage) {
        return new CallRecord(kind, usage, Duration.ofMillis(wallMillis), null, null);
    }

    private static CallKindTotals.Totals of(final CallKindTotals totals, final CallKind kind) {
        return Objects.requireNonNull(totals.totals().get(kind), "totals of " + kind);
    }

    // Two drafts and a review: each kind sums its own tokens and times and the other kind is untouched.
    @Test
    void record_twoKinds_sumsEachKindSeparately() {
        final CallKindTotals totals = CallKindTotals.of(List.of(
                answered(
                        CallKind.DRAFT,
                        3000,
                        new TokenUsage(800, 90, Duration.ofMillis(2500), Duration.ofMillis(400), 600)),
                answered(
                        CallKind.DRAFT,
                        2000,
                        new TokenUsage(700, 60, Duration.ofMillis(1500), Duration.ofMillis(300), 500)),
                answered(CallKind.PROOFREAD, 1000, new TokenUsage(500, 20, Duration.ofMillis(800)))));

        final CallKindTotals.Totals draft = of(totals, CallKind.DRAFT);
        assertThat(draft.attempts()).isEqualTo(2);
        assertThat(draft.failed()).isZero();
        assertThat(draft.elapsed()).isEqualTo(Duration.ofSeconds(5));
        assertThat(draft.promptTotal()).contains(1500L);
        assertThat(draft.completionTotal()).contains(150L);
        assertThat(draft.cachedTotal()).contains(1100L);
        assertThat(draft.generationTotal()).contains(Duration.ofSeconds(4));
        assertThat(draft.promptEvalTotal()).contains(Duration.ofMillis(700));
        final CallKindTotals.Totals review = of(totals, CallKind.PROOFREAD);
        assertThat(review.attempts()).isEqualTo(1);
        assertThat(review.promptTotal()).contains(500L);
        assertThat(review.generationTotal()).contains(Duration.ofMillis(800));
        assertThat(review.cachedTotal()).isEmpty();
        assertThat(review.promptEvalTotal()).isEmpty();
    }

    // 15i.B2: the knowledge question is a kind of its own, so the run summary and the report give it its own line.
    @Test
    void record_bookKnowledgeCall_getsItsOwnKindLine() {
        final CallKindTotals totals = CallKindTotals.of(List.of(
                answered(CallKind.BOOK_KNOWLEDGE, 4000, new TokenUsage(300, 90, Duration.ofSeconds(3))),
                answered(CallKind.BRIEF_SUGGESTION, 9000, new TokenUsage(900, 200, Duration.ofSeconds(8)))));

        assertThat(totals.totals()).containsOnlyKeys(CallKind.BOOK_KNOWLEDGE, CallKind.BRIEF_SUGGESTION);
        assertThat(of(totals, CallKind.BOOK_KNOWLEDGE).elapsed()).isEqualTo(Duration.ofSeconds(4));
    }

    // 15h.C5: the mean first-token time and its share count only the attempts that reported a prompt-evaluation time.
    @Test
    void record_firstTokenReported_meanAndShareOfOnlyThoseAttempts() {
        final CallKindTotals totals = CallKindTotals.of(List.of(
                answered(
                        CallKind.DRAFT,
                        3000,
                        new TokenUsage(800, 90, Duration.ofMillis(2500), Duration.ofMillis(400), 600)),
                answered(
                        CallKind.DRAFT,
                        2000,
                        new TokenUsage(700, 60, Duration.ofMillis(1500), Duration.ofMillis(300), 500)),
                answered(CallKind.PROOFREAD, 1000, new TokenUsage(500, 20, Duration.ofMillis(800)))));

        assertThat(of(totals, CallKind.DRAFT).meanFirstToken()).contains(Duration.ofMillis(350));
        assertThat(of(totals, CallKind.DRAFT).prefillShare()).contains(0.14);
        assertThat(of(totals, CallKind.PROOFREAD).meanFirstToken()).isEmpty();
        assertThat(of(totals, CallKind.PROOFREAD).prefillShare()).isEmpty();
    }

    // 15.2: two drafts, one with a 0.4 s first token and one with none: the mean is 0.4 s over the one call.
    @Test
    void record_oneDraftWith400msOneWithNone_meanFirstTokenIsFortyOverOneCall() {
        final CallKindTotals totals = CallKindTotals.of(List.of(
                answered(
                        CallKind.DRAFT,
                        3000,
                        new TokenUsage(800, 90, Duration.ofMillis(2500), Duration.ofMillis(400), null)),
                answered(CallKind.DRAFT, 2000, new TokenUsage(700, 60, Duration.ofMillis(1500)))));

        assertThat(of(totals, CallKind.DRAFT).meanFirstToken()).contains(Duration.ofMillis(400));
        assertThat(of(totals, CallKind.DRAFT).attempts()).isEqualTo(2);
    }

    // A first-token time the call measured itself wins over the provider's prompt-evaluation time.
    @Test
    void record_measuredFirstToken_isUsedBeforeThePromptEvaluationTime() {
        final CallKindTotals totals = CallKindTotals.of(List.of(new CallRecord(
                CallKind.DRAFT,
                new TokenUsage(800, 90, Duration.ofMillis(2500), Duration.ofMillis(400), null),
                Duration.ofMillis(4000),
                Duration.ofMillis(1000),
                null)));

        assertThat(of(totals, CallKind.DRAFT).meanFirstToken()).contains(Duration.ofSeconds(1));
        assertThat(of(totals, CallKind.DRAFT).prefillShare()).contains(0.25);
    }

    // A failed attempt counts as an attempt and a failure but adds no tokens; a reply with no usage adds only its time.
    @Test
    void record_failedAndUnreportedAttempts_countedWithoutTokens() {
        final CallKindTotals totals = CallKindTotals.of(List.of(
                new CallRecord(CallKind.DRAFT, null, Duration.ofSeconds(60), null, FailureReason.UNCLASSIFIED),
                new CallRecord(CallKind.DRAFT, null, Duration.ofSeconds(4), null, null)));

        final CallKindTotals.Totals draft = of(totals, CallKind.DRAFT);
        assertThat(draft.attempts()).isEqualTo(2);
        assertThat(draft.failed()).isEqualTo(1);
        assertThat(draft.elapsed()).isEqualTo(Duration.ofSeconds(64));
        assertThat(draft.promptTotal()).isEmpty();
        assertThat(draft.completionTotal()).isEmpty();
        assertThat(draft.generationTotal()).isEmpty();
        assertThat(draft.promptEvalTotal()).isEmpty();
        assertThat(draft.cachedTotal()).isEmpty();
    }

    // Three drafts cached 100, 300 and nothing reported: the share is over the two that reported, 400 of 1,600.
    @Test
    void add_missingFigure_isNotCountedAsZero() {
        final CallKindTotals totals = CallKindTotals.of(List.of(cached(100), cached(300), cached(null)));
        final CallKindTotals none = CallKindTotals.of(List.of(cached(null)));
        final CallKindTotals zero = CallKindTotals.of(List.of(cached(0)));

        assertThat(of(totals, CallKind.DRAFT).cachedTotal()).contains(400L);
        assertThat(of(totals, CallKind.DRAFT).cachedReported()).isEqualTo(2);
        assertThat(of(totals, CallKind.DRAFT).cachedShare()).contains(0.25);
        assertThat(of(none, CallKind.DRAFT).cachedTotal()).isEmpty();
        assertThat(of(none, CallKind.DRAFT).cachedShare()).isEmpty();
        assertThat(of(zero, CallKind.DRAFT).cachedTotal()).contains(0L);
        assertThat(of(zero, CallKind.DRAFT).cachedShare()).contains(0.0);
    }

    private static CallRecord cached(final @Nullable Integer cachedPrompt) {
        return answered(CallKind.DRAFT, 3000, new TokenUsage(800, 90, Duration.ofSeconds(2), null, cachedPrompt));
    }
}
