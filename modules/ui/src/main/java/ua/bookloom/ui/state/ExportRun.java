package ua.bookloom.ui.state;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelSelection;
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
 * <p>The model for the consistency pass is created on the background thread because creating one can load it, but
 * the selection is read on the FX thread, where the settings live.
 */
@Slf4j
final class ExportRun {

    private final ExportService service;
    private final ChatModelFactory models;
    private final SettingsViewModel settings;
    private final WorkflowProgress progress;
    private final ExecutorService executor;
    private final ActivityTracker activities;
    private final Messages messages;
    private final ExportRecorder recorder;
    private final ReplaceCheck replace;
    private final AtomicReference<@Nullable ExportJob> job = new AtomicReference<>();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private ActivityTracker.@Nullable Handle handle;
    private @Nullable ExportActivity shownActivity;
    private final ReadOnlyBooleanWrapper running = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyObjectWrapper<@Nullable ExportOutcome> outcome = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyStringWrapper failure = new ReadOnlyStringWrapper("");

    ExportRun(
            final ExportService service,
            final ChatModelFactory models,
            final SettingsViewModel settings,
            final WorkflowProgress progress,
            final ExecutorService executor,
            final ActivityTracker activities,
            final Messages messages,
            final ExportRecorder recorder,
            final ReplaceCheck replace) {
        this.service = Objects.requireNonNull(service, "service");
        this.models = Objects.requireNonNull(models, "models");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.progress = Objects.requireNonNull(progress, "progress");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.activities = Objects.requireNonNull(activities, "activities");
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
        final Optional<ModelSelection> selection = request.consistencyPass() ? settings.selection() : Optional.empty();
        log.info(
                "export of project {} to {} starting: side files {}, consistency pass {}, model chosen {}",
                request.projectId(),
                request.destination().getFileName(),
                request.sideFiles().stream().map(SideFile::name).sorted().toList(),
                request.consistencyPass(),
                selection.isPresent());
        running.set(true);
        stopped.set(false);
        job.set(null);
        final ExportActivity shown = register(request, selection);
        outcome.set(null);
        failure.set("");
        try {
            executor.execute(() -> checkThenWork(request, written, selection.orElse(null), shown));
        } catch (RejectedExecutionException rejected) {
            log.error("the export could not be submitted", rejected);
            finish(null, AppError.of(ErrorCode.internal, "Export failed", "The export could not be started."));
        }
    }

    /**
     * Ends the running export's last check early and writes the file with the fixes made so far; does nothing when no
     * export runs. FX thread only.
     */
    void writeNow() {
        final ExportJob created = job.get();
        log.info("export write now requested: running {}, job exists {}", running.get(), created != null);
        if (running.get() && created != null) {
            created.writeNow();
        }
    }

    /**
     * How far the running export's last check got.
     *
     * @return the tally; all zero when no export runs or its check has not started
     */
    ExportActivity.RereadTally rereadTally() {
        final ExportActivity shown = shownActivity;
        return shown == null ? new ExportActivity.RereadTally(0, 0, 0) : shown.tally();
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

    private ExportActivity register(final ExportRequest request, final Optional<ModelSelection> selection) {
        final ActivityTracker.Handle registered = activities.begin(ActivityKind.EXPORT, this::stop);
        handle = registered;
        final ExportActivity shown = new ExportActivity(
                registered,
                messages,
                request.destination(),
                selection.map(ModelSelection::modelId).orElse(null),
                activities::now);
        shown.announce(new ExportProgress(ExportProgress.Step.VALIDATING, 0, 1));
        shownActivity = shown;
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
    private void checkThenWork(
            final ExportRequest request,
            final List<Path> written,
            final @Nullable ModelSelection selection,
            final ExportActivity shown) {
        final List<Path> taken = request.overwrite() ? List.of() : existing(written);
        if (taken.isEmpty()) {
            work(request, selection, shown);
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
        return new ExportRequest(
                request.projectId(),
                request.destination(),
                true,
                request.sideFiles(),
                request.consistencyPass(),
                request.consistencyScope());
    }

    private void endActivity() {
        final ActivityTracker.Handle registered = handle;
        handle = null;
        shownActivity = null;
        if (registered != null) {
            registered.end();
        }
    }

    private void work(
            final ExportRequest request, final @Nullable ModelSelection selection, final ExportActivity shown) {
        ExportOutcome result = null;
        // Failed until the job answers: an Error (a stack overflow, out of memory) escapes the catch below, and the
        // finally must still end the activity, or its scrim and the disabled navigation would stay for good.
        AppError error = AppError.of(ErrorCode.internal, "Export failed", "The book could not be written.");
        try {
            final Result<ExportReport> ran = run(request, selection, shown);
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

    private Result<ExportReport> run(
            final ExportRequest request, final @Nullable ModelSelection selection, final ExportActivity shown) {
        ChatModel model = null;
        if (selection != null) {
            model = modelOrNull(selection);
        }
        final Result<ExportJob> built = service.newExport(request, model, shown);
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

    // The pass's name sweep needs no model, so a provider that is down must not stop the book being written.
    private @Nullable ChatModel modelOrNull(final ModelSelection selection) {
        final Result<ChatModel> created = models.create(selection);
        if (created.isErr()) {
            final AppError refused = Objects.requireNonNull(created.error(), "error");
            log.warn("no model for the consistency pass, its gender step is skipped: code {}", refused.code());
            return null;
        }
        return created.data();
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
