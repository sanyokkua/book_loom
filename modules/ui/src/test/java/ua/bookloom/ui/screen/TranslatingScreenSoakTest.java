package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import javafx.application.Platform;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.ChunkPosition;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.SegmentStarted;
import ua.bookloom.ui.ProgressFixtures;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * A night's run as the window sees it, in seconds: 3,700 decided segments and 20,000 log lines go from the job thread
 * through the real run session, state mirror and translating screen. The FX thread must keep answering, the log and
 * its list stay bounded, and the heap the window keeps does not grow with the run.
 */
// `slow`: thousands of events through the real screen; `test` and the gate run it, `fastTest` leaves it out.
@Tag("slow")
class TranslatingScreenSoakTest extends TranslatingScreenTestBase {

    private static final int SEGMENTS = 3_700;
    private static final int LOG_LINES = 20_000;
    private static final int CALLS_PER_SEGMENT = LOG_LINES / SEGMENTS;
    private static final Duration MAX_FX_LATENCY = Duration.ofMillis(500);
    private static final long MAX_GROWTH_MIB = 64;
    private static final long MIB = 1024L * 1024L;

    private final ScheduledExecutorService prober = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("fx-latency-probe").factory());
    private final AtomicLong worstLatencyNanos = new AtomicLong();
    private @Nullable ScheduledFuture<?> probing;

    @AfterEach
    void stopProbing() {
        final ScheduledFuture<?> started = probing;
        if (started != null) {
            started.cancel(true);
        }
        prober.shutdownNow();
    }

    @Test
    void run_thousandsOfSegmentsAndLogLines_keepsTheFxThreadResponsiveAndTheLogBounded(final TestReporter reporter)
            throws Exception {
        readyToStart();
        showTranslating();
        onFx(() -> button("translating-start").fire());
        job.awaitRunStarted();
        final long before = retainedMib();
        probeEvery(Duration.ofMillis(20));

        IntStream.range(0, SEGMENTS).forEach(this::emitSegment);
        job.drain();
        WaitForAsyncUtils.waitForFxEvents();
        awaitFx(() -> mirror().total().get() == SEGMENTS);

        final Duration worst = Duration.ofNanos(worstLatencyNanos.get());
        final long growth = retainedMib() - before;
        reporter.publishEntry("worstFxLatencyMs", String.valueOf(worst.toMillis()));
        reporter.publishEntry("heapGrowthMiB", String.valueOf(growth));

        assertThat(worst).isLessThan(MAX_FX_LATENCY);
        assertThat(ThemeTestSupport.onFx(() -> mirror().activityLog().size())).isLessThanOrEqualTo(500);
        assertThat(ThemeTestSupport.onFx(() -> logList().getItems().size())).isLessThanOrEqualTo(500);
        assertThat(ThemeTestSupport.onFx(() -> mirror().accepted().get())).isEqualTo(SEGMENTS - SEGMENTS / 10);
        assertThat(growth).isLessThan(MAX_GROWTH_MIB);
    }

    // One segment: its start, its calls (one in five failing), and its decision, every tenth one flagged.
    private void emitSegment(final int index) {
        final String id = "ch" + index / 50 + ".xhtml:" + index;
        job.emit(new SegmentStarted(id, "ch" + index / 50 + " · p" + index, "Text.", new ChunkPosition(1, 1, 1, 4)));
        IntStream.range(0, CALLS_PER_SEGMENT).forEach(call -> job.emit(callOf(id, call)));
        final int flagged = (index + 1) / 10;
        job.emit(new SegmentDecided(
                id,
                index % 10 == 9 ? SegmentStatus.FLAGGED : SegmentStatus.ACCEPTED,
                null,
                ProgressFixtures.progress(1, 1, index + 1 - flagged, flagged, SEGMENTS - index - 1)));
    }

    private static ModelCallFinished callOf(final String id, final int call) {
        return new ModelCallFinished(
                id,
                CallKind.DRAFT,
                Duration.ofSeconds(1),
                null,
                40,
                false,
                List.of(id),
                call + 1,
                call % 5 == 4 ? ErrorCode.timeout : null);
    }

    // How long a runLater posted from another thread waits for the FX thread, at a fixed cadence while events flow.
    private void probeEvery(final Duration period) {
        probing = prober.scheduleAtFixedRate(
                () -> {
                    final long posted = System.nanoTime();
                    final CountDownLatch ran = new CountDownLatch(1);
                    Platform.runLater(ran::countDown);
                    awaitQuietly(ran);
                    worstLatencyNanos.accumulateAndGet(System.nanoTime() - posted, Math::max);
                },
                0,
                period.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    private static void awaitQuietly(final CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }

    private static long retainedMib() {
        System.gc();
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / MIB;
    }
}
