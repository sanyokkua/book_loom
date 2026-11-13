package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelCapabilities;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.ProviderProbe;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.pipeline.TranslationEngine;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.api.project.BookBrief;
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
    private final Provider<ReviewMode> reviewMode;
    private final TranslationRunner runner;
    private final SessionReporter reporter;
    private final ExecutorService executor;
    private final ProviderVerifier verifier;
    private final ModelCapabilities capabilities;
    private final ActivityTracker activities;

    /**
     * Creates the starter.
     *
     * @param current the open book a run is started on
     * @param ports the ports a model and a job are created through, a paused run's provider is probed through, and
     *     the model's context length is read through
     * @param reviewMode how the run's pauses for review are chosen, asked again at each start
     * @param runner the runner that owns the one active run
     * @param reporter what tells the detailed log which provider, model and brief the run uses
     * @param executor the daemon executor a run is prepared on, never the FX thread
     * @param activities where the preparation is registered, so the window waits for it
     */
    @Inject
    public RunStarter(
            final CurrentProject current,
            final RunPorts ports,
            final Provider<ReviewMode> reviewMode,
            final TranslationRunner runner,
            final SessionReporter reporter,
            @BackgroundExecutor final ExecutorService executor,
            final ActivityTracker activities) {
        this.current = Objects.requireNonNull(current, "current");
        this.models = Objects.requireNonNull(ports, "ports").models();
        this.engine = ports.engine();
        this.reviewMode = Objects.requireNonNull(reviewMode, "reviewMode");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.reporter = Objects.requireNonNull(reporter, "reporter");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.verifier = ports.verifier();
        this.capabilities = ports.capabilities();
        this.activities = Objects.requireNonNull(activities, "activities");
    }

    /**
     * The review mode a run started now would use.
     *
     * @return the mode now, so a change in Settings shows on the next read; never null
     */
    public ReviewMode reviewMode() {
        return reviewMode.get();
    }

    /**
     * How a preparation ended.
     *
     * @param failure the error that stopped the preparation, or {@code null} when the runner took the run
     * @param loadsWithOwnSettings whether the server said it will load the model with settings of its own (LM Studio,
     *     a model not in memory), so the window may differ from the one the run budgets for; never a reason to stop
     */
    public record Prepared(@Nullable AppError failure, boolean loadsWithOwnSettings) {}

    /**
     * Prepares a run on the open book in the background and starts it. FX thread only, because it reads the open
     * book there.
     *
     * @param selection the provider and model to translate with
     * @param whenPrepared told, on the background thread and exactly once, how the preparation ended
     */
    public void start(final ModelSelection selection, final Consumer<Prepared> whenPrepared) {
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(whenPrepared, "whenPrepared");
        final OpenedBook book = Objects.requireNonNull(current.book().get(), "an open book, checked by the caller");
        log.debug("run requested: project {}", book.projectId());
        final ActivityTracker.Handle preparing = activities.begin(ActivityKind.RUN_PREPARATION, null);
        preparing.indeterminate(fileNameOf(book));
        final Consumer<Prepared> ending = prepared -> {
            Platform.runLater(preparing::end);
            whenPrepared.accept(prepared);
        };
        try {
            executor.execute(() -> prepareOffThread(book, selection, ending));
        } catch (RejectedExecutionException rejected) {
            log.error("the run could not be submitted for preparation", rejected);
            ending.accept(new Prepared(internalError(rejected), false));
        }
    }

    private void prepareOffThread(
            final OpenedBook book, final ModelSelection selection, final Consumer<Prepared> whenPrepared) {
        log.debug("preparing a run on {}", Thread.currentThread().getName());
        AppError failure = null;
        boolean loads = false;
        try {
            // Read once, here and not on the FX thread (the stored setting is a database read): the request, the
            // context and the pause points of this run must agree on one mode.
            final ReviewMode mode = reviewMode.get();
            log.debug("preparing the run with review mode {}", mode);
            failure = prepare(book, selection, mode);
            loads = failure == null && loadsWithOwnSettings(selection);
        } catch (RuntimeException thrown) {
            log.error("preparing the run threw instead of returning a result", thrown);
            failure = internalError(thrown);
        } finally {
            // Also on an Error: the caller's busy flag must never outlive a preparation that will not publish anything.
            whenPrepared.accept(new Prepared(failure, loads));
        }
    }

    // Asked once the run is taken, so the answer never delays or stops it; a server that cannot say counts as no.
    private boolean loadsWithOwnSettings(final ModelSelection selection) {
        try {
            final boolean loads = capabilities.loadsWithOwnSettings(selection);
            log.debug("the server loads {} with its own settings: {}", selection.modelId(), loads);
            return loads;
        } catch (RuntimeException thrown) {
            log.warn("whether the server loads the model by itself could not be asked; the run goes on", thrown);
            return false;
        }
    }

    private @Nullable AppError prepare(final OpenedBook book, final ModelSelection selection, final ReviewMode mode) {
        final Result<ChatModel> model = models.create(selection);
        if (model.isErr()) {
            log.debug("no model was created: code {}", errorCode(model));
            return model.error();
        }
        final Integer detected = capabilities.detectedTokens(selection).orElse(null);
        log.debug("the model's detected context length: {}", detected);
        final Result<TranslationJob> created = engine.newJob(
                new RunRequest(book.projectId(), mode, detected, selection.modelId(), selection),
                Objects.requireNonNull(model.data(), "model"));
        if (created.isErr()) {
            log.debug("no job was created: code {}", errorCode(created));
            return created.error();
        }
        final TranslationJob job = Objects.requireNonNull(created.data(), "job");
        job.pauseAt(pausePoints(mode));
        job.recoverWith(ProviderProbe.of(verifier, selection));
        final BookBrief brief = briefOf();
        final RunContext context = new RunContext(book.projectId(), fileNameOf(book), mode, brief.dial(), selection);
        reporter.runStarting(context, brief);
        final boolean began = runner.start(job, context);
        log.debug("the runner accepted the run: {}", began);
        return null;
    }

    /** The mode's own points plus the pause on a model error, which every window run honours. */
    private Set<PausePoint> pausePoints(final ReviewMode mode) {
        final Set<PausePoint> points = EnumSet.of(PausePoint.ON_ERROR);
        points.addAll(mode.pausePoints());
        return Set.copyOf(points);
    }

    // The brief as the person has left it, not the one the project was created with; it exists whenever a book does.
    private BookBrief briefOf() {
        return Objects.requireNonNull(current.brief().get(), "the open book's brief");
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
        return AppErrors.unexpected("The run could not be started because of an unexpected error.", cause);
    }
}
