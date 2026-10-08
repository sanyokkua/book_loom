package ua.bookloom.ui;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.ConsistencySummary;
import ua.bookloom.api.pipeline.ExportJob;
import ua.bookloom.api.pipeline.ExportProgress;
import ua.bookloom.api.pipeline.ExportProgressListener;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.ExportService;

/**
 * A hand-written {@link ExportService} that records each request and answers with one scripted report, or one scripted
 * error when the export is scripted to fail.
 */
public final class ScriptedExportService implements ExportService {

    private static final long HOLD_SECONDS = 10;

    private final List<ExportRequest> requests = new CopyOnWriteArrayList<>();
    private final List<@Nullable ChatModel> models = new CopyOnWriteArrayList<>();
    private final List<Boolean> ranOnFxThread = new CopyOnWriteArrayList<>();
    private volatile @Nullable AppError failure;
    private volatile @Nullable ExportReport report;
    private volatile boolean isWritingFiles;
    private final List<ExportProgress> scriptedProgress = new CopyOnWriteArrayList<>();
    private final List<CallSnapshot> scriptedCalls = new CopyOnWriteArrayList<>();
    private final AtomicInteger cancels = new AtomicInteger();
    private volatile @Nullable CountDownLatch hold;

    /** Makes every successful export answer with {@code scripted}; {@code null} answers a one-segment report. */
    public void reportWith(@Nullable final ExportReport scripted) {
        report = scripted;
    }

    /** Makes every successful export create its destination file, as a real one does, holding one byte. */
    public void writeFiles(final boolean writing) {
        isWritingFiles = writing;
    }

    /** Makes every export from now on fail with {@code error}; {@code null} makes them succeed again. */
    public void failWith(@Nullable final AppError error) {
        failure = error;
    }

    @Override
    public Result<ExportJob> newExport(final ExportRequest request, @Nullable final ChatModel model) {
        return newExport(request, model, ExportProgressListener.NONE);
    }

    @Override
    public Result<ExportJob> newExport(
            final ExportRequest request, @Nullable final ChatModel model, final ExportProgressListener progress) {
        requests.add(Objects.requireNonNull(request, "request"));
        models.add(model);
        return Result.ok(new ScriptedExportJob(request, progress));
    }

    /** Makes every job announce {@code events} when it runs, in order, before it answers. */
    public void announce(final ExportProgress... events) {
        scriptedProgress.clear();
        scriptedProgress.addAll(List.of(events));
    }

    /** Makes every job show {@code calls} when it runs, in order, after its progress and before it answers. */
    public void announceCalls(final CallSnapshot... calls) {
        scriptedCalls.clear();
        scriptedCalls.addAll(List.of(calls));
    }

    /** Makes every job wait, after announcing, until it is cancelled or {@link #release} is called. */
    public void holdRuns() {
        hold = new CountDownLatch(1);
    }

    /** Lets a held job finish normally. */
    public void release() {
        final CountDownLatch gate = hold;
        if (gate != null) {
            gate.countDown();
        }
    }

    /** How many times a job was cancelled. */
    public int cancels() {
        return cancels.get();
    }

    /** Every request asked for, in order. */
    public List<ExportRequest> requests() {
        return List.copyOf(requests);
    }

    /** The model each request was made with, in order; an entry is {@code null} when none was passed. */
    public List<@Nullable ChatModel> models() {
        return Collections.unmodifiableList(new ArrayList<>(models));
    }

    /** One entry per {@code run()}: {@code true} when it ran on the FX Application Thread. */
    public List<Boolean> ranOnFxThread() {
        return List.copyOf(ranOnFxThread);
    }

    private final class ScriptedExportJob implements ExportJob {

        private final ExportRequest request;
        private final ExportProgressListener progress;
        private volatile boolean isCancelled;

        ScriptedExportJob(final ExportRequest request, final ExportProgressListener progress) {
            this.request = request;
            this.progress = progress;
        }

        @Override
        public Result<ExportReport> run() {
            ranOnFxThread.add(Platform.isFxApplicationThread());
            scriptedProgress.forEach(progress::onProgress);
            scriptedCalls.forEach(progress::onCall);
            final CountDownLatch gate = hold;
            if (gate != null && !awaits(gate) && isCancelled) {
                return Result.err(AppError.of(ErrorCode.cancelled, "Export cancelled", "Nothing was written."));
            }
            final AppError error = failure;
            if (error != null) {
                return Result.err(error);
            }
            writeDestination();
            final ExportReport scripted = report;
            if (scripted != null) {
                return Result.ok(scripted);
            }
            final Path destination = request.destination();
            return Result.ok(
                    new ExportReport(destination, 1, 0, 0, 0, 0, 0, List.of(), 0, ConsistencySummary.NOT_RUN, 0));
        }

        private void writeDestination() {
            if (!isWritingFiles) {
                return;
            }
            try {
                Files.write(request.destination(), new byte[1]);
            } catch (IOException cause) {
                throw new UncheckedIOException(cause);
            }
        }

        private boolean awaits(final CountDownLatch gate) {
            try {
                return gate.await(HOLD_SECONDS, TimeUnit.SECONDS) && !isCancelled;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        @Override
        public void cancel() {
            cancels.incrementAndGet();
            isCancelled = true;
            release();
        }
    }
}
