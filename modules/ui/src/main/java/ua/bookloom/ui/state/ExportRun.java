package ua.bookloom.ui.state;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ExportJob;
import ua.bookloom.api.pipeline.ExportProgress;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.ExportService;
import ua.bookloom.api.pipeline.SideFile;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.i18n.ErrorWords;
import ua.bookloom.ui.i18n.Messages;

/**
 * One export at a time: builds the job, runs it and reads the written file's size off the FX thread, and keeps the
 * outcome or the failure for the screen. Every property is touched on the FX thread only.
 *
 * <p>The export makes no model call, so no model is chosen or created for it.
 */
@Slf4j
final class ExportRun {

    private final ExportService service;
    private final WorkflowProgress progress;
    private final ExecutorService executor;
    private final ActivityTracker activities;
    private final Messages messages;
    private final ExportRecorder recorder;
    private final ReplaceCheck replace;
    private final AtomicReference<@Nullable ExportJob> job = new AtomicReference<>();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private ActivityTracker.@Nullable Handle handle;
    private final ReadOnlyBooleanWrapper running = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyObjectWrapper<@Nullable ExportOutcome> outcome = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyStringWrapper failure = new ReadOnlyStringWrapper("");

    ExportRun(
            final ExportEngine engine,
            final ExportWork work,
            final ExecutorService executor,
            final Messages messages,
            final ExportRecorder recorder,
            final ReplaceCheck replace) {
        this.service = Objects.requireNonNull(engine, "engine").exports();
        this.progress = Objects.requireNonNull(work, "work").progress();
        this.executor = Objects.requireNonNull(executor, "executor");
        this.activities = work.activities();
        this.messages = Objects.requireNonNull(messages, "messages");
        this.recorder = Objects.requireNonNull(recorder, "recorder");
        this.replace = Objects.requireNonNull(replace, "replace");
    }

    ReadOnlyBooleanProperty running() {
        return running.getReadOnlyProperty();
    }

    ReadOnlyObjectProperty<@Nullable ExportOutcome> outcome() {
        return outcome.getReadOnlyProperty();
    }

    ReadOnlyStringProperty failure() {
        return failure.getReadOnlyProperty();
    }

    /**
     * Starts the export in the background; does nothing while one is already running. When it may not replace files
     * and one of {@code written} exists, it asks first and writes nothing unless the person allows it. FX thread only.
     *
     * @param request what to export
     * @param written every file the export writes, the book first, which are looked for before anything else
     */
    void start(final ExportRequest request, final List<Path> written) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(written, "written");
        if (running.get()) {
            log.debug("export not started: one is already running");
            return;
        }
        log.info(
                "export of project {} to {} starting: side files {}",
                request.projectId(),
                request.destination().getFileName(),
                request.sideFiles().stream().map(SideFile::name).sorted().toList());
        running.set(true);
        stopped.set(false);
        job.set(null);
        final ExportActivity shown = register(request);
        outcome.set(null);
        failure.set("");
        try {
            executor.execute(() -> checkThenWork(request, written, shown));
        } catch (RejectedExecutionException rejected) {
            log.error("the export could not be submitted", rejected);
            finish(null, AppError.of(ErrorCode.internal, "Export failed", "The export could not be started."));
        }
    }

    /** The file the shown outcome wrote, or null when no outcome is shown. FX thread only. */
    @Nullable
    Path justExported() {
        final ExportOutcome done = outcome.get();
        return done == null ? null : done.report().destination();
    }

    // A result describes the choices that produced it; once one changes it would vouch for a file the next press will
    // not write. A run still in flight is left alone, because its answer has not arrived yet.
    void forgetStale(final String why) {
        if (!running.get() && (outcome.get() != null || !failure.get().isEmpty())) {
            log.debug("the last export result is forgotten: {} changed", why);
            clear();
        }
    }

    /** Forgets the last outcome and failure, which described another book or other choices. FX thread only. */
    void clear() {
        log.debug("the last export outcome is forgotten");
        outcome.set(null);
        failure.set("");
    }

    private ExportActivity register(final ExportRequest request) {
        final ActivityTracker.Handle registered = activities.begin(ActivityKind.EXPORT, this::stop);
        handle = registered;
        final ExportActivity shown =
                new ExportActivity(registered, messages, request.destination(), null, activities::now);
        shown.announce(new ExportProgress(ExportProgress.Step.VALIDATING, 0, 1));
        return shown;
    }

    // The job exists only once the background thread has built it, so a stop pressed before then is replayed on it.
    private void stop() {
        stopped.set(true);
        final ExportJob created = job.get();
        log.info("export stop requested, job exists: {}", created != null);
        if (created != null) {
            created.cancel();
        }
    }

    // Files are looked for off the FX thread, before the job exists, so declining writes nothing and calls no model.
    private void checkThenWork(final ExportRequest request, final List<Path> written, final ExportActivity shown) {
        final List<Path> taken = request.overwrite() ? List.of() : existing(written);
        if (taken.isEmpty()) {
            work(request, shown);
            return;
        }
        log.debug("export of project {} waits for the replace question", request.projectId());
        Platform.runLater(() -> askFirst(request, written, taken));
    }

    private static List<Path> existing(final List<Path> written) {
        try {
            return written.stream().filter(Files::exists).toList();
        } catch (RuntimeException unreadable) {
            log.warn("whether the export's files exist could not be read; the export refuses a taken path", unreadable);
            return List.of();
        }
    }

    private void askFirst(final ExportRequest request, final List<Path> written, final List<Path> taken) {
        running.set(false);
        endActivity();
        replace.ask(taken, () -> start(withOverwrite(request), written));
    }

    private static ExportRequest withOverwrite(final ExportRequest request) {
        return new ExportRequest(request.projectId(), request.destination(), true, request.sideFiles());
    }

    private void endActivity() {
        final ActivityTracker.Handle registered = handle;
        handle = null;
        if (registered != null) {
            registered.end();
        }
    }

    private void work(final ExportRequest request, final ExportActivity shown) {
        ExportOutcome result = null;
        // Failed until the job answers: an Error (a stack overflow, out of memory) escapes the catch below, and the
        // finally must still end the activity, or its scrim and the disabled navigation would stay for good.
        AppError error = AppError.of(ErrorCode.internal, "Export failed", "The book could not be written.");
        try {
            final Result<ExportReport> ran = run(request, shown);
            if (ran.isOk()) {
                final ExportReport report = Objects.requireNonNull(ran.data(), "report");
                // Written and checked: the window keeps it on the project, so the tiles read it after a restart.
                recorder.record(request.projectId(), report);
                result = new ExportOutcome(report, sizeOf(report.destination()));
                error = null;
            } else {
                error = ran.error();
            }
        } catch (RuntimeException thrown) {
            log.error("the export threw instead of returning a result", thrown);
        } finally {
            final ExportOutcome published = result;
            final AppError failed = error;
            log.debug(
                    "export work ended on {}: written {}",
                    Thread.currentThread().getName(),
                    published != null);
            Platform.runLater(() -> finish(published, failed));
        }
    }

    private Result<ExportReport> run(final ExportRequest request, final ExportActivity shown) {
        final Result<ExportJob> built = service.newExport(request, null, shown);
        if (built.isErr()) {
            return Result.err(Objects.requireNonNull(built.error(), "error"));
        }
        final ExportJob created = Objects.requireNonNull(built.data(), "job");
        job.set(created);
        if (stopped.get()) {
            created.cancel();
        }
        return created.run();
    }

    private long sizeOf(final Path file) {
        try {
            return Files.size(file);
        } catch (IOException unreadable) {
            log.warn("the size of the written book could not be read", unreadable);
            return 0;
        }
    }

    private void finish(final @Nullable ExportOutcome done, final @Nullable AppError error) {
        running.set(false);
        endActivity();
        if (done != null) {
            final ExportReport report = done.report();
            log.info(
                    "export finished: {} segments written, {} pending, {} kept as source, {} flagged, {} bytes,"
                            + " {} written in source (broken formatting or flagged with no target)",
                    report.written(),
                    report.pending(),
                    report.sourceKept(),
                    report.flaggedWritten(),
                    done.sizeBytes(),
                    report.sourceFallbacks().size());
            outcome.set(done);
            progress.markDone(ViewNames.EXPORT);
            return;
        }
        final AppError refused = Objects.requireNonNull(error, "an error when nothing was written");
        log.warn("export refused with code {}", refused.code());
        failure.set(ErrorWords.message(messages, refused));
    }
}
