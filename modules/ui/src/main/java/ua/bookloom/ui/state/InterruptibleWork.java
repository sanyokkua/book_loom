package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * One piece of background work the person can stop: a stop that arrives while the work runs interrupts its thread (the
 * provider client turns an interrupt into a {@code cancelled} answer), and a stop that arrives before it starts makes
 * it answer without running.
 *
 * <p>The thread is interrupted only while it is inside {@link #run}, and never after it leaves: a stop claims the
 * running thread with one compare-and-set before it interrupts it, and the work, as it ends, either releases the thread
 * first (no interrupt can follow) or finds it claimed, waits for that one interrupt to be delivered and clears it. So a
 * stop can never leak into the next task the pooled thread takes.
 */
@Slf4j
final class InterruptibleWork {

    private static final int IDLE = 0;
    private static final int RUNNING = 1;
    private static final int INTERRUPTING = 2;
    private static final int INTERRUPTED = 3;
    private static final int FINISHED = 4;

    private final AtomicBoolean stopped = new AtomicBoolean();
    private final AtomicInteger state = new AtomicInteger(IDLE);
    private volatile @Nullable Thread worker;
    private final Consumer<Thread> interrupter;

    /** Creates work that interrupts its thread with {@link Thread#interrupt()}. */
    InterruptibleWork() {
        this(Thread::interrupt);
    }

    /**
     * Creates work that interrupts its thread through {@code interrupter}, so a test can hold a stop between claiming
     * the thread and interrupting it.
     *
     * @param interrupter interrupts the given thread; non-null
     */
    InterruptibleWork(final Consumer<Thread> interrupter) {
        this.interrupter = Objects.requireNonNull(interrupter, "interrupter");
    }

    /** Asks the work to stop; callable from any thread, and harmless once the work has ended. */
    void stop() {
        stopped.set(true);
        final Thread thread = worker;
        final boolean claimed = thread != null && state.compareAndSet(RUNNING, INTERRUPTING);
        log.debug("work asked to stop, running now: {}", claimed);
        if (claimed) {
            try {
                interrupter.accept(thread);
            } finally {
                state.set(INTERRUPTED);
            }
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
        worker = Thread.currentThread();
        state.set(RUNNING);
        try {
            return stopped.get() ? whenStopped.get() : work.get();
        } finally {
            release();
        }
    }

    // A stop that claimed the thread interrupts it once; the interrupt is waited for and cleared here, never left for
    // the next task.
    private void release() {
        if (state.compareAndSet(RUNNING, FINISHED)) {
            worker = null;
            return;
        }
        while (state.get() == INTERRUPTING) {
            Thread.onSpinWait();
        }
        final boolean cleared = Thread.interrupted();
        worker = null;
        state.set(FINISHED);
        log.debug("work ended after a stop; its interrupt cleared: {}", cleared);
    }
}
