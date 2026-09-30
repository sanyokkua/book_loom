package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.pipeline.TranslationEngine;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * Builds a run on the project the window has open and hands it to the {@link TranslationRunner}: it creates the
 * model, then a job over the stored project, and starts it. The same call starts a first run and, after a stop, a
 * new job that begins at the project's first pending segment, because the project keeps every decision.
 *
 * <p>Building the model and the job can load a model, so the work goes to the background executor and the outcome is
 * reported through a callback that runs on that thread; the caller moves it to the FX thread. The book is never
 * imported here: the window imported it once, and the project id the run names is the one it holds.
 */
@Slf4j
@Singleton
public final class RunStarter {

    private final CurrentProject current;
    private final ChatModelFactory models;
    private final TranslationEngine engine;
    private final ReviewMode reviewMode;
    private final TranslationRunner runner;
    private final ExecutorService executor;

    /**
     * Creates the starter.
     *
     * @param current the open book a run is started on
     * @param models the port a model is created through
     * @param engine the port a job is created through
     * @param reviewMode how the run's pauses for review are chosen, resolved once at launch
     * @param runner the runner that owns the one active run
     * @param executor the daemon executor a run is prepared on, never the FX thread
     */
    @Inject
    public RunStarter(
            final CurrentProject current,
            final ChatModelFactory models,
            final TranslationEngine engine,
            final ReviewMode reviewMode,
            final TranslationRunner runner,
            @BackgroundExecutor final ExecutorService executor) {
        this.current = Objects.requireNonNull(current, "current");
        this.models = Objects.requireNonNull(models, "models");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.reviewMode = Objects.requireNonNull(reviewMode, "reviewMode");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * The review mode every run of this launch uses.
     *
     * @return the mode; never null
     */
    public ReviewMode reviewMode() {
        return reviewMode;
    }

    /**
     * Prepares a run on the open book in the background and starts it. FX thread only, because it reads the open
     * book there.
     *
     * @param request where the completed run's book is written
     * @param selection the provider and model to translate with
     * @param whenPrepared told, on the background thread and exactly once, {@code null} when the runner took the run
     *     or the error that stopped the preparation
     */
    public void start(
            final InterimRunRequest request,
            final ModelSelection selection,
            final Consumer<@Nullable AppError> whenPrepared) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(whenPrepared, "whenPrepared");
        final OpenedBook book = Objects.requireNonNull(current.book().get(), "an open book, checked by the caller");
        log.debug("run requested: project {}, review mode {}", book.projectId(), reviewMode);
        try {
            executor.execute(() -> prepareOffThread(book, request, selection, whenPrepared));
        } catch (RejectedExecutionException rejected) {
            log.error("the run could not be submitted for preparation", rejected);
            whenPrepared.accept(internalError(rejected));
        }
    }

    private void prepareOffThread(
            final OpenedBook book,
            final InterimRunRequest request,
            final ModelSelection selection,
            final Consumer<@Nullable AppError> whenPrepared) {
        log.debug("preparing a run on {}", Thread.currentThread().getName());
        AppError failure = null;
        try {
            failure = prepare(book, request, selection);
        } catch (RuntimeException thrown) {
            log.error("preparing the run threw instead of returning a result", thrown);
            failure = internalError(thrown);
        } finally {
            // Also on an Error: the caller's busy flag must never outlive a preparation that will not publish anything.
            whenPrepared.accept(failure);
        }
    }

    private @Nullable AppError prepare(
            final OpenedBook book, final InterimRunRequest request, final ModelSelection selection) {
        final Result<ChatModel> model = models.create(selection);
        if (model.isErr()) {
            log.debug("no model was created: code {}", errorCode(model));
            return model.error();
        }
        final Result<TranslationJob> created = engine.newJob(
                new RunRequest(book.projectId(), reviewMode), Objects.requireNonNull(model.data(), "model"));
        if (created.isErr()) {
            log.debug("no job was created: code {}", errorCode(created));
            return created.error();
        }
        final TranslationJob job = Objects.requireNonNull(created.data(), "job");
        job.pauseAt(pausePoints());
        final RunContext context =
                new RunContext(book.projectId(), fileNameOf(book), reviewMode, dialOf(), selection, request);
        final boolean began = runner.start(job, context);
        log.debug("the runner accepted the run: {}", began);
        return null;
    }

    /** The mode's own points plus the pause on a model error, which every window run honours. */
    private Set<PausePoint> pausePoints() {
        final Set<PausePoint> points = EnumSet.of(PausePoint.ON_ERROR);
        points.addAll(reviewMode.pausePoints());
        return Set.copyOf(points);
    }

    // The brief as the person has left it, not the one the project was created with; it exists whenever a book does.
    private QualityDial dialOf() {
        return Objects.requireNonNull(current.brief().get(), "the open book's brief")
                .dial();
    }

    private static String fileNameOf(final OpenedBook book) {
        final Path name = book.source().getFileName();
        return name == null ? book.source().toString() : name.toString();
    }

    private static @Nullable ErrorCode errorCode(final Result<?> result) {
        final AppError error = result.error();
        return error == null ? null : error.code();
    }

    private static AppError internalError(final Throwable cause) {
        return AppError.of(
                ErrorCode.internal,
                "Unexpected error",
                "The run could not be started because of an unexpected error.",
                null,
                cause);
    }
}
