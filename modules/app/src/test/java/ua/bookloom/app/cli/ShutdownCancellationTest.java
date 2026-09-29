package ua.bookloom.app.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ShutdownCancellationTest {

    private static final Duration SHORT_WAIT = Duration.ofMillis(300);

    // WHEN the hook fires while a job blocks in run, THEN run cancels it and returns once the job has ended.
    @Test
    void run_jobBlockedUntilCancelled_cancelsItAndReturnsWhenItEnds() throws InterruptedException {
        final ShutdownCancellation shutdown = new ShutdownCancellation();
        final BlockingJob job = new BlockingJob(shutdown);
        final Thread jobThread = Thread.ofVirtual().start(job::run);
        job.awaitStarted();

        final long started = System.nanoTime();
        shutdown.run();
        final Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        jobThread.join(5_000);

        assertThat(job.cancelCalls()).isEqualTo(1);
        assertThat(job.ended()).isTrue();
        assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
    }

    // WHEN a held action ignores the cancel, THEN run gives up waiting after the configured limit.
    @Test
    void run_heldActionIgnoresCancel_returnsAfterTheWaitLimit() {
        final ShutdownCancellation shutdown = new ShutdownCancellation(SHORT_WAIT);
        final AtomicInteger cancels = new AtomicInteger();
        shutdown.hold(cancels::incrementAndGet);

        final long started = System.nanoTime();
        shutdown.run();
        final Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(cancels.get()).isEqualTo(1);
        assertThat(elapsed).isBetween(SHORT_WAIT.minusMillis(50), Duration.ofSeconds(5));
    }

    // WHEN the command already finished, THEN run returns at once without waiting.
    @Test
    void run_commandAlreadyFinished_returnsWithoutWaiting() {
        final ShutdownCancellation shutdown = new ShutdownCancellation(Duration.ofSeconds(30));
        shutdown.finished();

        final long started = System.nanoTime();
        shutdown.run();

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
    }

    // WHEN an action is held after the hook has fired, THEN it is cancelled at once, so a later export is not missed.
    @Test
    void hold_afterShutdownRequested_cancelsTheActionImmediately() {
        final ShutdownCancellation shutdown = new ShutdownCancellation(Duration.ofMillis(10));
        final AtomicInteger cancels = new AtomicInteger();
        shutdown.run();

        shutdown.hold(cancels::incrementAndGet);

        assertThat(cancels.get()).isEqualTo(1);
    }

    /** A job whose run blocks until its cancel is called, then reports it ended and counts down the latch. */
    private static final class BlockingJob {
        private final ShutdownCancellation shutdown;
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch cancelled = new CountDownLatch(1);
        private final AtomicInteger cancelCalls = new AtomicInteger();
        private volatile boolean ended;

        BlockingJob(ShutdownCancellation shutdown) {
            this.shutdown = shutdown;
            shutdown.hold(this::cancel);
        }

        void run() {
            started.countDown();
            try {
                cancelled.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            ended = true;
            shutdown.finished();
        }

        void cancel() {
            cancelCalls.incrementAndGet();
            cancelled.countDown();
        }

        void awaitStarted() throws InterruptedException {
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        }

        int cancelCalls() {
            return cancelCalls.get();
        }

        boolean ended() {
            return ended;
        }
    }
}
