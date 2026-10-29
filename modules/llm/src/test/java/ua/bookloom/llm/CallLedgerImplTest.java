package ua.bookloom.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.llm.CallRecord;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.CallKind;

/**
 * The ledger keeps what each call reported as it was reported, in order, bounded, and safe to append from the job and
 * an interactive caller at once. Averaging over the calls that reported a figure is the pipeline's sum (its
 * {@code CallKindTotalsTest}); this proves the missing figure reaches it as missing.
 */
class CallLedgerImplTest {

    private static final int THREADS = 4;
    private static final int PER_THREAD = 2_500;

    @Test
    void record_callsWithMissingCachedFigure_averagesOverTheOnesThatReported() {
        final CallLedgerImpl ledger = new CallLedgerImpl();

        ledger.record(draft(100));
        ledger.record(draft(300));
        ledger.record(draft(null));

        assertThat(ledger.since(0))
                .extracting(record ->
                        Objects.requireNonNull(record.usage(), "usage").cachedPrompt())
                .containsExactly(100, 300, null);
    }

    @Test
    void since_mark_returnsOnlyTheRecordsAfterItInOrder() {
        final CallLedgerImpl ledger = new CallLedgerImpl();
        ledger.record(failed(CallKind.DRAFT));
        final long mark = ledger.mark();

        ledger.record(failed(CallKind.REVIEW));
        ledger.record(failed(CallKind.SUMMARY));

        assertThat(ledger.since(mark))
                .extracting(CallRecord::callKind)
                .containsExactly(CallKind.REVIEW, CallKind.SUMMARY);
        assertThat(ledger.since(ledger.mark())).isEmpty();
    }

    @Test
    void record_oneMoreThanTheCap_keepsTheCapAndDropsTheOldest() {
        final CallLedgerImpl ledger = new CallLedgerImpl();
        ledger.record(failed(CallKind.REVIEW));

        IntStream.range(0, CallLedgerImpl.MAX_RECORDS).forEach(ignored -> ledger.record(failed(CallKind.DRAFT)));

        final List<CallRecord> kept = ledger.since(0);
        assertThat(kept).hasSize(100_000);
        assertThat(kept).extracting(CallRecord::callKind).containsOnly(CallKind.DRAFT);
    }

    @Test
    void record_fromFourThreadsAtOnce_keepsEveryRecord() throws InterruptedException {
        final CallLedgerImpl ledger = new CallLedgerImpl();
        final ExecutorService threads = Executors.newFixedThreadPool(THREADS);

        IntStream.range(0, THREADS)
                .forEach(ignored -> threads.execute(
                        () -> IntStream.range(0, PER_THREAD).forEach(call -> ledger.record(failed(CallKind.DRAFT)))));
        threads.shutdown();

        assertThat(threads.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(ledger.since(0)).hasSize(10_000);
        assertThat(ledger.mark()).isEqualTo(10_000);
    }

    private static CallRecord draft(final @Nullable Integer cached) {
        return new CallRecord(
                CallKind.DRAFT,
                new TokenUsage(800, 90, Duration.ofSeconds(2), Duration.ofMillis(400), cached),
                Duration.ofSeconds(3),
                null,
                null);
    }

    private static CallRecord failed(final CallKind kind) {
        return new CallRecord(kind, null, Duration.ofSeconds(1), null, FailureReason.UNCLASSIFIED);
    }
}
