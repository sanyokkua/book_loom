package ua.bookloom.pipeline.export;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.ExportProgress;
import ua.bookloom.api.pipeline.ExportProgressListener;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * The seam the consistency pass reaches the model through during an export: it refuses a call once the export is
 * cancelled, counts the finished calls of the stage the pass announced, and lets a cancel interrupt the call that is
 * waiting on the provider (the client turns the interrupt into {@code cancelled}).
 */
@Slf4j
final class ExportCalls implements ModelCalls {

    private final ModelCalls delegate;
    private final AtomicBoolean cancelled;
    private final ExportProgressListener progress;
    private final AtomicReference<@Nullable Thread> waiting = new AtomicReference<>();
    private ExportProgress.Step step = ExportProgress.Step.CONSISTENCY_RETRY;
    private int done;
    private int total;
    private boolean countsCalls = true;

    ExportCalls(final ModelCalls delegate, final AtomicBoolean cancelled, final ExportProgressListener progress) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.cancelled = Objects.requireNonNull(cancelled, "cancelled");
        this.progress = Objects.requireNonNull(progress, "progress");
    }

    @Override
    public Result<ChatResponse> call(final CallKind kind, @Nullable final String segmentId, final ChatRequest request) {
        if (cancelled.get()) {
            log.debug("export model call refused: the export is cancelled kind={}", kind);
            return Result.err(BookExporter.cancelledBeforeWriting());
        }
        waiting.set(Thread.currentThread());
        try {
            return delegate.call(kind, segmentId, request);
        } finally {
            waiting.set(null);
            // A cancel racing the end of the call must not leave the flag on the thread that goes on to write files.
            if (cancelled.get()) {
                Thread.interrupted();
            }
            finished();
        }
    }

    @Override
    public void planned(final Stage stage, final int calls) {
        step = switch (stage) {
            case GENDER_RETRY -> ExportProgress.Step.CONSISTENCY_RETRY;
            case RETRY_DOUBTED -> ExportProgress.Step.RETRY_DOUBTED;
            case NEIGHBOUR_CHECK -> ExportProgress.Step.CONSISTENCY_NEIGHBOUR;
        };
        countsCalls = stage != Stage.RETRY_DOUBTED;
        done = 0;
        total = calls;
        log.debug("export pass stage={} plannedCalls={}", stage, calls);
        if (total > 0) {
            progress.onProgress(new ExportProgress(step, 0, total));
        }
    }

    @Override
    public void advanced(final Stage stage, final int units) {
        log.debug("export pass stage={} advanced done={} of {}", stage, units, total);
        if (total > 0 && !countsCalls) {
            done = Math.min(units, total);
            progress.onProgress(new ExportProgress(step, done, total));
        }
    }

    /** Interrupts the call now waiting on the provider, if any; a no-op between calls. */
    void interrupt() {
        final Thread thread = waiting.get();
        if (thread != null) {
            log.debug("export cancel interrupts the model call in flight");
            thread.interrupt();
        }
    }

    private void finished() {
        if (total == 0 || !countsCalls) {
            return;
        }
        done = Math.min(done + 1, total);
        progress.onProgress(new ExportProgress(step, done, total));
    }
}
