package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.SegmentView;

/**
 * The two reads a run makes from the review desk while it goes: the flagged queue after each flagged decision, and
 * the kept-as-source count when the run ends. Both happen off the FX thread and publish into the mirror's live
 * section.
 *
 * <p>A failed read leaves what is shown as it was: the desk logged the failure where it built the error, and a stale
 * queue is better than an empty one.
 */
@Slf4j
final class ReviewDeskReads {

    private final ReviewDesk desk;
    private final Executor executor;
    private final LiveSection live;
    private final String projectId;
    private final AtomicLong requested = new AtomicLong();
    private final ReentrantLock readLock = new ReentrantLock();

    ReviewDeskReads(final ReviewDesk desk, final Executor executor, final LiveSection live, final String projectId) {
        this.desk = Objects.requireNonNull(desk, "desk");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.live = Objects.requireNonNull(live, "live");
        this.projectId = Objects.requireNonNull(projectId, "projectId");
    }

    /** Asks for the queue to be read again on the background executor; requests made meanwhile share one read. */
    void refreshFlaggedQueue() {
        final long ticket = requested.incrementAndGet();
        log.debug("flagged queue refresh {} requested for project {}", ticket, projectId);
        executor.execute(() -> readQueue(ticket));
    }

    /** Reads the counts on the calling thread, which must not be the FX thread, and publishes the kept-as-source. */
    void publishSourceKept() {
        final Result<ReviewCounts> counts = desk.counts(projectId);
        final ReviewCounts data = counts.data();
        if (data == null) {
            log.debug("the kept-as-source count could not be read for project {}", projectId);
            return;
        }
        log.debug("run ended: {} segments kept as source in project {}", data.sourceKept(), projectId);
        live.publishSourceKept(data.sourceKept());
    }

    private void readQueue(final long ticket) {
        readLock.lock();
        try {
            if (ticket < requested.get()) {
                log.debug("flagged queue refresh {} skipped, a newer one follows", ticket);
                return;
            }
            final Result<List<SegmentView>> queue = desk.queue(projectId, ReviewFilter.ALL_FLAGGED);
            final List<SegmentView> views = queue.data();
            if (views == null) {
                log.debug("flagged queue refresh {} failed, the queue is left as it was", ticket);
                return;
            }
            live.publishFlaggedQueue(views.stream().map(FlaggedRow::of).toList());
        } finally {
            readLock.unlock();
        }
    }
}
