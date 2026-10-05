package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads the desk's flagged count off the FX thread and publishes only the newest read.
 *
 * <p>Two reads can finish out of order on a pool executor, and a run stores its decisions in its last flush, after its
 * last event: the count read while it ran is short by what that flush stores. So a read answers only if no newer one was
 * asked for, and a run that stops writing is counted again.
 */
@Slf4j
final class FlaggedCount {

    private final ReviewQueries queries;
    private final ExecutorService executor;
    private final AtomicLong ticket = new AtomicLong();

    FlaggedCount(final ReviewQueries queries, final ExecutorService executor) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Asks for a new read when a run enters a state in which it has stored everything it decided.
     *
     * @param state the run's new state
     * @param refresh asks for the read
     */
    void afterRun(final RunState state, final Runnable refresh) {
        final boolean stored = state == RunState.COMPLETED
                || state == RunState.STOPPED
                || state == RunState.FAILED
                || state == RunState.PAUSED;
        if (stored) {
            log.debug("run state {}: reading the flagged count again after the final flush", state);
            refresh.run();
        }
    }

    /**
     * Reads the count of a project and publishes it on the FX thread unless a newer read was asked for.
     *
     * @param projectId the project the open book is stored under
     * @param shown the count now shown, answered when the desk cannot say
     * @param publish receives the count on the FX thread
     */
    void read(final String projectId, final int shown, final IntConsumer publish) {
        Objects.requireNonNull(publish, "publish");
        final long mine = ticket.incrementAndGet();
        executor.execute(() -> {
            final int flagged = queries.flagged(projectId, shown);
            Platform.runLater(() -> {
                if (mine == ticket.get()) {
                    publish.accept(flagged);
                } else {
                    log.debug("a count of {} read for an older request is dropped", flagged);
                }
            });
        });
    }
}
