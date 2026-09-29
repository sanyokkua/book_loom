package ua.bookloom.app.cli;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

/**
 * Cancels the command's running job or export when the process is asked to stop (Ctrl+C), then gives it a few
 * seconds to unwind, so an export removes its hidden temporary file instead of being cut off with it in place.
 *
 * <p>A cancel held after the request has already arrived is applied at once: the command moves from the translation
 * to the export without a gap the hook could fall into.
 */
@Slf4j
@Singleton
public final class ShutdownCancellation implements Runnable {

    private static final Duration DEFAULT_WAIT = Duration.ofSeconds(5);

    private final Duration wait;
    private final AtomicReference<Runnable> held = new AtomicReference<>(() -> {});
    private final AtomicBoolean requested = new AtomicBoolean();
    private final CountDownLatch finished = new CountDownLatch(1);

    /** Waits the production limit of five seconds. */
    @Inject
    public ShutdownCancellation() {
        this(DEFAULT_WAIT);
    }

    ShutdownCancellation(Duration wait) {
        this.wait = Objects.requireNonNull(wait, "wait");
    }

    /** Records the action that cancels the job or export now running, replacing the previous one. */
    public void hold(Runnable cancel) {
        Objects.requireNonNull(cancel, "cancel");
        held.set(cancel);
        if (requested.get()) {
            log.debug("shutdown already requested, cancelling the action held now");
            cancel.run();
        }
    }

    /** Called by the command when its run has returned, whatever the outcome. */
    public void finished() {
        finished.countDown();
    }

    /** Cancels the held action and waits for the command to finish, for at most the configured limit. */
    @Override
    public void run() {
        requested.set(true);
        log.info("translate shutdown requested, cancelling the running job or export");
        Objects.requireNonNull(held.get(), "held").run();
        final boolean ended = awaitFinished();
        log.info("translate shutdown cancellation endedWithinLimit={} limit={}", ended, wait);
    }

    private boolean awaitFinished() {
        try {
            return finished.await(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
