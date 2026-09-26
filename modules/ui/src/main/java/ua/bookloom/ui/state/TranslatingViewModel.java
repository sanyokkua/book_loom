package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.TranslationEngine;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.api.pipeline.TranslationRequest;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.notify.ErrorPresenter;
import ua.bookloom.ui.notify.Toasts;

/**
 * What the translating dashboard does: start a run from the brief and the chosen model, pause, resume and stop it,
 * announce that it finished, and route every failure to the one surface its code is assigned.
 *
 * <p>A singleton because the screen is rebuilt on every visit while a run outlives it, and because it holds the one
 * completion listener: it is registered on the mirror in the constructor and held in a field, since a listener the
 * mirror held only weakly around an unreferenced lambda would be collected and the toast would silently stop. The
 * screen's own listeners are weak, as they must be for a controller the mirror outlives.
 *
 * <p>Everything here that touches a property runs on the FX Application Thread. Building the model and the job can
 * load a model, so it goes to the background executor, and the busy flag is cleared only by a task the FX queue runs,
 * on every way out, so that it can never be seen cleared before the run it guards has been published.
 *
 * <p>A failed preparation and a run that returns a failure go through the same {@code route} method, so one code can
 * never reach two different surfaces depending on when it happened. A start is refused by naming the first missing
 * input, book before model. The target language is not among them: the brief defaults it and accepts only the
 * supported languages, so it can never be blank when a start is pressed.
 */
@Slf4j
@Singleton
public final class TranslatingViewModel {

    private final StateMirror mirror;
    private final TranslationRunner runner;
    private final BookBriefViewModel brief;
    private final SettingsViewModel settings;
    private final ChatModelFactory models;
    private final TranslationEngine engine;
    private final Toasts toasts;
    private final ErrorPresenter errors;
    private final ExecutorService executor;
    private final ReadOnlyBooleanWrapper preparing = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyObjectWrapper<Controls> controls = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<@Nullable RunNotice> notice = new ReadOnlyObjectWrapper<>();
    private final ChangeListener<RunState> onRunState = (observed, was, now) -> onRunStateChanged(now);
    private final ChangeListener<String> onModelText = (observed, was, now) -> onModelTextChanged();
    private final ChangeListener<@Nullable OpenedBook> onOpenedBook = (observed, was, now) -> onBookChanged(now);

    /**
     * Registers the completion listener on the mirror and derives the first set of controls.
     *
     * @param mirror the state every run publishes into and this view model observes
     * @param runner the runner that owns the one active run
     * @param brief where the request is assembled from
     * @param settings where the provider and model come from
     * @param models the port a model is created through
     * @param engine the port a job is created through
     * @param toasts where a finished run is announced
     * @param errors where a failure whose code is assigned the blocking dialog is shown
     * @param executor the daemon executor a run is prepared on, never the FX thread
     */
    @Inject
    public TranslatingViewModel(
            final StateMirror mirror,
            final TranslationRunner runner,
            final BookBriefViewModel brief,
            final SettingsViewModel settings,
            final ChatModelFactory models,
            final TranslationEngine engine,
            final Toasts toasts,
            final ErrorPresenter errors,
            @BackgroundExecutor final ExecutorService executor) {
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.brief = Objects.requireNonNull(brief, "brief");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.models = Objects.requireNonNull(models, "models");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.toasts = Objects.requireNonNull(toasts, "toasts");
        this.errors = Objects.requireNonNull(errors, "errors");
        this.executor = Objects.requireNonNull(executor, "executor");
        refreshControls();
        preparing.addListener((observed, was, now) -> refreshControls());
        mirror.runState().addListener((observed, was, now) -> refreshControls());
        mirror.runState().addListener(onRunState);
        settings.model().addListener(onModelText);
        brief.openedBook().addListener(onOpenedBook);
        log.debug("translating view model ready");
    }

    /**
     * Which run controls the current state offers.
     *
     * @return a read-only property that always holds a value; FX thread only
     */
    public ReadOnlyObjectProperty<Controls> controls() {
        return controls.getReadOnlyProperty();
    }

    /**
     * What the banner says instead of the plain run state: a provider failure, a refusal, or the input a start was
     * missing.
     *
     * @return a read-only property holding {@code null} when the plain state banner applies; FX thread only
     */
    public ReadOnlyObjectProperty<@Nullable RunNotice> notice() {
        return notice.getReadOnlyProperty();
    }

    /**
     * Whether a start is still building its model and job.
     *
     * @return a read-only property; FX thread only
     */
    public ReadOnlyBooleanProperty preparing() {
        return preparing.getReadOnlyProperty();
    }

    /**
     * Starts a run from the brief and the chosen model, if the state allows it. Returns at once; the run is prepared in
     * the background and appears through the mirror. A start with no book or no model starts nothing and publishes a
     * notice naming the first of them that is missing. FX thread only.
     */
    public void start() {
        final Controls offered = controls.get();
        if (!offered.start().isEnabled() && !offered.newRun().isEnabled()) {
            log.debug(
                    "start refused: not offered in state {}, preparing {}",
                    mirror.runState().get(),
                    preparing.get());
            return;
        }
        final Optional<ModelSelection> selection = settings.selection();
        final Optional<RunNotice.Input> missing = missingInput(selection.isPresent());
        if (missing.isPresent()) {
            log.debug("start refused: {} is missing", missing.get());
            notice.set(new RunNotice.MissingInput(missing.get()));
            return;
        }
        notice.set(null);
        final Optional<TranslationRequest> request = brief.request();
        if (request.isEmpty()) {
            log.debug("start refused: the brief could not be turned into a request");
            return;
        }
        final ModelSelection chosen = selection.orElseThrow();
        log.info("start requested: provider {}, model {}", chosen.providerId(), chosen.modelId());
        preparing.set(true);
        submit(request.get(), chosen);
    }

    /** Asks the run to pause at its next boundary, if the pause control is offered. FX thread only. */
    public void pause() {
        if (offered("pause", controls.get().pause())) {
            runner.pause();
        }
    }

    /** Asks a paused run to continue, if the resume control is offered. FX thread only. */
    public void resume() {
        if (offered("resume", controls.get().resume())) {
            runner.resume();
        }
    }

    /** Asks the run to end, if the stop control is offered. FX thread only. */
    public void stop() {
        if (offered("stop", controls.get().stop())) {
            runner.cancel();
        }
    }

    private void onModelTextChanged() {
        clearIfSupplied(RunNotice.Input.MODEL, () -> settings.selection().isPresent());
    }

    private void onBookChanged(final @Nullable OpenedBook book) {
        clearIfSupplied(RunNotice.Input.BOOK, () -> book != null);
    }

    /** Withdraws a refusal that named {@code input} once that input exists, so a ready start never reads as refused. */
    private void clearIfSupplied(final RunNotice.Input input, final BooleanSupplier supplied) {
        if (notice.get() instanceof RunNotice.MissingInput missing
                && missing.which() == input
                && supplied.getAsBoolean()) {
            log.debug("{} is now supplied: withdrawing the missing-input notice", input);
            notice.set(null);
        }
    }

    private Optional<RunNotice.Input> missingInput(final boolean modelChosen) {
        if (brief.openedBook().get() == null) {
            return Optional.of(RunNotice.Input.BOOK);
        }
        return modelChosen ? Optional.empty() : Optional.of(RunNotice.Input.MODEL);
    }

    private boolean offered(final String control, final ControlState state) {
        final boolean enabled = state.isEnabled();
        log.debug(
                "{} pressed in state {}: honoured {}",
                control,
                mirror.runState().get(),
                enabled);
        return enabled;
    }

    private void submit(final TranslationRequest request, final ModelSelection selection) {
        try {
            executor.execute(() -> prepareOffThread(request, selection));
        } catch (RejectedExecutionException rejected) {
            log.error("the run could not be submitted for preparation", rejected);
            finishPreparing(internalError(rejected));
        }
    }

    private void prepareOffThread(final TranslationRequest request, final ModelSelection selection) {
        log.debug("preparing a run on {}", Thread.currentThread().getName());
        AppError failure = null;
        try {
            failure = prepare(request, selection);
        } catch (RuntimeException thrown) {
            log.error("preparing the run threw instead of returning a result", thrown);
            failure = internalError(thrown);
        } finally {
            // Also on an Error: the busy flag must never outlive a preparation that will not publish anything.
            finishPreparing(failure);
        }
    }

    private @Nullable AppError prepare(final TranslationRequest request, final ModelSelection selection) {
        final Result<ChatModel> model = models.create(selection);
        if (model.isErr()) {
            log.debug("no model was created: code {}", errorCode(model));
            return model.error();
        }
        final Result<TranslationJob> created = engine.newJob(request, Objects.requireNonNull(model.data(), "model"));
        if (created.isErr()) {
            log.debug("no job was created: code {}", errorCode(created));
            return created.error();
        }
        final TranslationJob job = Objects.requireNonNull(created.data(), "job");
        // Same as the command line: the job may pause only when this screen asks it to.
        job.pauseAt(Set.of());
        final boolean began = runner.start(job, request, selection);
        log.debug("the runner accepted the run: {}", began);
        return null;
    }

    /** Clears the busy flag on the FX thread, then routes the failure, if any, to the surface its code is assigned. */
    private void finishPreparing(final @Nullable AppError failure) {
        Platform.runLater(() -> {
            preparing.set(false);
            if (failure != null) {
                route(failure);
            }
        });
    }

    /**
     * The one place a failure chooses its surface, for a failed preparation and a run that ends on an error alike. Only
     * the code is logged, never the cause or the details.
     */
    private void route(final AppError error) {
        final FailureSurface surface = FailureSurface.of(error.code());
        log.debug("failure code {} -> surface {}", error.code(), surface);
        switch (surface) {
            case PROVIDER_ERROR -> {
                log.warn("provider error: code {}", error.code());
                notice.set(new RunNotice.ProviderError(error));
            }
            case IN_PLACE -> notice.set(new RunNotice.Refused(error));
            case DIALOG -> errors.present(error);
            case STOPPED -> log.debug("a stop is shown by the stopped state; nothing more to show");
            case SETTINGS_ONLY ->
                log.warn("code {} belongs to the model list, not to a run; the run's own banner stands", error.code());
        }
    }

    private void refreshControls() {
        controls.set(Controls.of(mirror.runState().get(), preparing.get()));
    }

    private void onRunStateChanged(final RunState now) {
        switch (now) {
            case RUNNING -> notice.set(null);
            case FAILED -> routeRunFailure();
            case COMPLETED -> announceCompletion();
            default -> log.debug("run state is now {}; nothing to route or announce", now);
        }
    }

    private void routeRunFailure() {
        final AppError failure = mirror.failure().get();
        if (failure == null) {
            log.warn("the run failed but no failure was published to route");
            return;
        }
        route(failure);
    }

    private void announceCompletion() {
        final JobReport report = mirror.report().get();
        final int accepted =
                report != null ? report.accepted() : mirror.accepted().get();
        final int flagged = report != null ? report.flagged() : mirror.flagged().get();
        log.debug("run completed: {} accepted, {} flagged", accepted, flagged);
        if (flagged > 0) {
            toasts.warning(MessageKey.TOAST_RUN_FINISHED_FLAGGED, accepted, flagged);
        } else {
            toasts.success(MessageKey.TOAST_RUN_FINISHED, accepted);
        }
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
