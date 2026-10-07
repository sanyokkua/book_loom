package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * One piece of background work the person can stop: a stop that arrives while the work runs interrupts its thread (the
 * provider client turns an interrupt into a {@code cancelled} answer), and a stop that arrives before it starts makes
 * it answer without running. The thread is interrupted only while it is inside {@link #run}, and its interrupt flag is
 * cleared when it leaves, so a stop can never leak into the next task the pooled thread takes.
 */
@Slf4j
final class InterruptibleWork {

    private final AtomicBoolean stopped = new AtomicBoolean();
    private final AtomicReference<@Nullable Thread> worker = new AtomicReference<>();

    /** Asks the work to stop; callable from any thread, and harmless once the work has ended. */
    void stop() {
        stopped.set(true);
        final Thread thread = worker.get();
        log.debug("work asked to stop, running now: {}", thread != null);
        if (thread != null) {
            thread.interrupt();
        }
    }

    /**
     * Runs {@code work} on the calling thread unless it was stopped before it began.
     *
     * @param work what to run; non-null
     * @param whenStopped the answer for work stopped before it began; non-null
     * @return what the work answered, or {@code whenStopped} if it never began
     */
    <T> T run(final Supplier<T> work, final Supplier<T> whenStopped) {
        Objects.requireNonNull(work, "work");
        Objects.requireNonNull(whenStopped, "whenStopped");
        worker.set(Thread.currentThread());
        try {
            return stopped.get() ? whenStopped.get() : work.get();
        } finally {
            worker.set(null);
            if (stopped.get()) {
                // Clears an interrupt that arrived as the work ended; the pooled thread goes on to other tasks.
                Thread.interrupted();
            }
        }
    }
}
