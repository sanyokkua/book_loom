package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.Optional;
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
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.project.BookBrief;
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
 * load a model, so the {@link RunStarter} does it on the background executor, and the busy flag is cleared only by a
 * task the FX queue runs, on every way out, so that it can never be seen cleared before the run it guards has been
 * published. Resuming a stopped run asks for a new job over the same project, which begins at its first pending
 * segment; resuming a paused one continues the same job.
 *
 * <p>A failed preparation is routed by its code through {@link FailureSurface}; a run that ends on a failure is routed
 * by its state (stopped, refused in place, or the blocking dialog), and a pause on a model error, whatever its code,
 * is the provider-error state that Resume continues from. A start is refused by naming the first missing
 * input, in the order book, source language, target language, model.
 */
@Slf4j
@Singleton
public final class TranslatingViewModel {

    private final StateMirror mirror;
    private final TranslationRunner runner;
    private final ExportViewModel exports;
    private final CurrentProject current;
    private final SettingsViewModel settings;
    private final RunStarter starter;
    private final Toasts toasts;
    private final ErrorPresenter errors;
    private final ReadOnlyBooleanWrapper preparing = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper pendingRemain = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyObjectWrapper<Controls> controls = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<@Nullable RunNotice> notice = new ReadOnlyObjectWrapper<>();
    private final ChangeListener<RunState> onRunState = (observed, was, now) -> onRunStateChanged(now);
    private final ChangeListener<String> onModelText = (observed, was, now) -> onModelTextChanged();
    private final ChangeListener<@Nullable OpenedBook> onOpenedBook = (observed, was, now) -> onBookChanged(now);
    private final ChangeListener<@Nullable BookBrief> onBrief = (observed, was, now) -> onBriefChanged(now);
    private final ChangeListener<@Nullable AppError> onProviderError = (observed, was, now) -> onPausedOnError(now);

    /**
     * Registers the completion listener on the mirror and derives the first set of controls.
     *
     * @param mirror the state every run publishes into and this view model observes
     * @param runner the runner that owns the one active run
     * @param exports where the completed run's book is written until the export screen does it
     * @param current the open book a run is started on
     * @param settings where the provider and model come from
     * @param starter what builds a run on the open book and starts it
     * @param toasts where a finished run is announced
     * @param errors where a failure whose code is assigned the blocking dialog is shown
     */
    @Inject
    public TranslatingViewModel(
            final StateMirror mirror,
            final TranslationRunner runner,
            final ExportViewModel exports,
            final CurrentProject current,
            final SettingsViewModel settings,
            final RunStarter starter,
            final Toasts toasts,
            final ErrorPresenter errors) {
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.exports = Objects.requireNonNull(exports, "exports");
        this.current = Objects.requireNonNull(current, "current");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.starter = Objects.requireNonNull(starter, "starter");
        this.toasts = Objects.requireNonNull(toasts, "toasts");
        this.errors = Objects.requireNonNull(errors, "errors");
        refreshControls();
        preparing.addListener((observed, was, now) -> refreshControls());
        pendingRemain.addListener((observed, was, now) -> refreshControls());
        mirror.runState().addListener((observed, was, now) -> refreshControls());
        mirror.runState().addListener(onRunState);
        settings.model().addListener(onModelText);
        current.book().addListener(onOpenedBook);
        current.brief().addListener(onBrief);
        mirror.review().providerError().addListener(onProviderError);
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
     * Whether the project still holds segments no run has decided, which a completed run needs to offer a start. It
     * stays false until the dashboard reads the project's pending count.
     *
     * @return a read-only property; FX thread only
     */
    public ReadOnlyBooleanProperty pendingRemain() {
        return pendingRemain.getReadOnlyProperty();
    }

    /**
     * Starts a run from the brief and the chosen model, if the state allows it. Returns at once; the run is prepared in
     * the background and appears through the mirror. A start with no book or no model starts nothing and publishes a
     * notice naming the first of them that is missing. FX thread only.
     */
    public void start() {
        if (!controls.get().start().isEnabled()) {
            log.debug(
                    "start refused: not offered in state {}, preparing {}",
                    mirror.runState().get(),
                    preparing.get());
            return;
        }
        begin(true);
    }

    /** Asks the run to pause at its next boundary, if the pause control is offered. FX thread only. */
    public void pause() {
        if (offered("pause", controls.get().pause())) {
            runner.pause();
        }
    }

    /**
     * Continues a run, if the resume control is offered: a paused run resumes the same job, while a stopped one gets a
     * new job over the same project that begins at its first pending segment. FX thread only.
     */
    public void resume() {
        if (!offered("resume", controls.get().resume())) {
            return;
        }
        if (mirror.runState().get() == RunState.STOPPED) {
            log.debug("resume after a stop: a new job over the same project");
            begin(false);
        } else {
            log.debug("resume from a pause: the same job continues");
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

    private void onBriefChanged(final @Nullable BookBrief brief) {
        clearIfSupplied(RunNotice.Input.SOURCE_LANGUAGE, () -> brief != null && brief.sourceLanguage() != null);
        clearIfSupplied(RunNotice.Input.TARGET_LANGUAGE, () -> brief != null && brief.targetLanguage() != null);
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
        final BookBrief brief = current.brief().get();
        if (brief == null) {
            return Optional.of(RunNotice.Input.BOOK);
        }
        if (brief.sourceLanguage() == null) {
            return Optional.of(RunNotice.Input.SOURCE_LANGUAGE);
        }
        if (brief.targetLanguage() == null) {
            return Optional.of(RunNotice.Input.TARGET_LANGUAGE);
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

    private void begin(final boolean announce) {
        final Optional<ModelSelection> selection = settings.selection();
        final Optional<RunNotice.Input> missing = missingInput(selection.isPresent());
        if (missing.isPresent()) {
            log.debug("run refused: {} is missing", missing.get());
            notice.set(new RunNotice.MissingInput(missing.get()));
            return;
        }
        notice.set(null);
        final Optional<InterimRunRequest> request = exports.interimExport();
        if (request.isEmpty()) {
            log.debug("run refused: the destination names no usable path");
            return;
        }
        final ModelSelection chosen = selection.orElseThrow();
        log.info("run requested: provider {}, model {}", chosen.providerId(), chosen.modelId());
        preparing.set(true);
        starter.start(request.get(), chosen, failure -> finishPreparing(failure, announce));
    }

    /** Clears the busy flag on the FX thread, then routes the failure, if any, to the surface its code is assigned. */
    private void finishPreparing(final @Nullable AppError failure, final boolean announce) {
        Platform.runLater(() -> {
            preparing.set(false);
            if (failure != null) {
                route(failure);
            } else if (announce) {
                toasts.info(MessageKey.TOAST_RUN_STARTED);
            }
        });
    }

    /**
     * The surface of a failed preparation, chosen from its code. A preparation has no segment to flag and no review
     * retry to warn about, so those two surfaces fall back to the blocking dialog. Only the code is logged, never the
     * cause or the details.
     */
    private void route(final AppError error) {
        final FailureSurface surface = FailureSurface.of(error.code());
        log.debug("preparation failure code {} -> surface {}", error.code(), surface);
        switch (surface) {
            case PROVIDER_ERROR -> notice.set(new RunNotice.ProviderError(error));
            case IN_PLACE -> notice.set(new RunNotice.Refused(error));
            case DIALOG, FLAGGED_SEGMENT, WARNING_TOAST -> errors.present(error);
            case STOPPED -> log.debug("a stop is shown by the stopped state; nothing more to show");
            case SETTINGS_ONLY ->
                log.debug("code {} belongs to the model list, not to a run; the run's own banner stands", error.code());
        }
    }

    private void refreshControls() {
        final RunState state = mirror.runState().get();
        final Controls next = Controls.of(state, preparing.get(), pendingRemain.get());
        log.debug(
                "controls for state {} preparing {} pending remain {}: {}",
                state,
                preparing.get(),
                pendingRemain.get(),
                next);
        controls.set(next);
    }

    private void onPausedOnError(final @Nullable AppError error) {
        if (error != null) {
            log.debug("paused on error {}: showing the provider-error state", error.code());
            notice.set(new RunNotice.ProviderError(error));
        }
    }

    private void onRunStateChanged(final RunState now) {
        switch (now) {
            case RUNNING -> notice.set(null);
            case FAILED -> routeRunFailure();
            case COMPLETED -> announceCompletion();
            default -> log.debug("run state is now {}; nothing to route or announce", now);
        }
    }

    // A run's own outcome is routed by its state: a refused start (validation) is shown in place, any other failure
    // opens the blocking dialog. A cancellation never gets here, because it ends the run stopped.
    private void routeRunFailure() {
        final AppError failure = mirror.failure().get();
        if (failure == null) {
            log.warn("the run failed but no failure was published to route");
            return;
        }
        if (failure.code() == ErrorCode.validation) {
            log.debug("the run was refused with {}: shown in place", failure.code());
            notice.set(new RunNotice.Refused(failure));
        } else {
            log.debug("the run failed with {}: opening the error dialog", failure.code());
            errors.present(failure);
        }
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
}
