package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** A stop that interrupts the work's thread never leaks the interrupt into the next task of the pooled thread. */
class InterruptibleWorkTest {

    private static final long WAIT_SECONDS = 5;

    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final CountDownLatch inWork = new CountDownLatch(1);
    private final CountDownLatch finishWork = new CountDownLatch(1);
    private final CountDownLatch claimed = new CountDownLatch(1);
    private final CountDownLatch proceed = new CountDownLatch(1);
    private final AtomicBoolean stopDone = new AtomicBoolean();

    @AfterEach
    void stopThePool() {
        pool.shutdownNow();
    }

    // The stop has claimed the thread but not interrupted it yet; the test decides when it does.
    private void heldInterrupt(final Thread thread) {
        claimed.countDown();
        awaitQuietly(proceed);
        thread.interrupt();
    }

    private static void awaitQuietly(final CountDownLatch latch) {
        try {
            assertThat(latch.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private String work() {
        inWork.countDown();
        awaitQuietly(finishWork);
        // An interrupt that reached the work itself is the work's answer to the stop; clear it as a client would.
        Thread.interrupted();
        return "done";
    }

    // The pooled thread's state as the next task would find it, read once the stop has returned.
    private boolean interruptedAfterTheStopReturned(final InterruptibleWork running) {
        running.run(this::work, () -> "stopped");
        while (!stopDone.get()) {
            Thread.onSpinWait();
        }
        return Thread.currentThread().isInterrupted();
    }

    // IF a stop that claimed the thread as the work ended could interrupt it after run() had cleared the flag, THEN the
    // interrupt would leak into whatever the pooled thread ran next.
    @Test
    void run_stopInterruptsAsTheWorkEnds_leavesThePooledThreadUninterrupted() throws Exception {
        final InterruptibleWork running = new InterruptibleWork(this::heldInterrupt);
        final CompletableFuture<Boolean> leaked =
                CompletableFuture.supplyAsync(() -> interruptedAfterTheStopReturned(running), pool);
        awaitQuietly(inWork);
        final CompletableFuture<Void> stopping = CompletableFuture.runAsync(
                () -> {
                    running.stop();
                    stopDone.set(true);
                },
                pool);
        awaitQuietly(claimed);

        finishWork.countDown();
        proceed.countDown();
        stopping.get(WAIT_SECONDS, TimeUnit.SECONDS);

        assertThat(leaked.get(WAIT_SECONDS, TimeUnit.SECONDS)).isFalse();
    }

    // IF a stop before the work began still ran it, THEN Stop pressed early would do nothing.
    @Test
    void run_stoppedBeforeItBegan_answersWithoutRunning() {
        final InterruptibleWork running = new InterruptibleWork();
        running.stop();

        assertThat(running.run(() -> "ran", () -> "stopped")).isEqualTo("stopped");
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }
}
