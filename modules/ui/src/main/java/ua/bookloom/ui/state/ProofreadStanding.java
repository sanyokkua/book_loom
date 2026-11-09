package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.ProofreadProgress;
import ua.bookloom.api.pipeline.ProofreadTally;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * Where the open project's Proofread stage stands while no run is at work, so a reopened or stopped project says
 * "Continues with Proofread at window 41 of 120." (design D15 "Resume"), and whether its code part has work left — a
 * renamed target to sweep, a deferral to settle — which a Continue does at every dial. It is read in the background,
 * with no model call, whenever the open book changes, a run settles, or the glossary is written while no run is at work (a gender set
 * after a finished run marks segments for Proofread at once); the windows are planned with the chosen model's batch
 * size, as the run will plan them, or the built-in one when no model is chosen. A read that a newer one overtook is
 * dropped. FX thread only, apart from that read.
 */
@Slf4j
@Singleton
public final class ProofreadStanding {

    private final ProofreadProgress progress;
    private final ModelBatchSize batchSizes;
    private final SettingsViewModel settings;
    private final CurrentProject current;
    private final Executor background;
    private final Executor fx;
    private final ReadOnlyObjectWrapper<@Nullable ProofreadTally> tally = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyBooleanWrapper codeWork = new ReadOnlyBooleanWrapper();
    // Held so the read-only view of the glossary rows, which follows its source only weakly, lives as long as this.
    private final Observable glossary;
    private long asked;

    /**
     * Follows the open project and the run's state.
     *
     * @param ports the planner of the windows and the reader of the chosen model's batch size
     * @param settings the chosen model
     * @param current the open project
     * @param mirror the run's state, whose settling reads the tally again
     * @param background where the windows are planned, never the FX thread
     * @param names the glossary rows as stored, whose change reads the tally again while no run is at work
     */
    @Inject
    public ProofreadStanding(
            final Ports ports,
            final SettingsViewModel settings,
            final CurrentProject current,
            final StateMirror mirror,
            final @BackgroundExecutor ExecutorService background,
            final NamesStyleViewModel names) {
        this(ports, settings, current, mirror, background, Platform::runLater, names.rows());
    }

    ProofreadStanding(
            final Ports ports,
            final SettingsViewModel settings,
            final CurrentProject current,
            final StateMirror mirror,
            final Executor background,
            final Executor fx,
            final Observable glossary) {
        this.progress = Objects.requireNonNull(ports, "ports").progress();
        this.batchSizes = ports.batchSizes();
        this.settings = Objects.requireNonNull(settings, "settings");
        this.current = Objects.requireNonNull(current, "current");
        this.background = Objects.requireNonNull(background, "background");
        this.fx = Objects.requireNonNull(fx, "fx");
        this.glossary = Objects.requireNonNull(glossary, "glossary");
        current.book().addListener((observed, was, now) -> refresh());
        // A run at work reads the glossary itself and its settling reads the tally again.
        this.glossary.addListener(observed -> refreshWhileNoRun(mirror, "glossary written"));
        // A desk action can take the last work away (an applied proposal) while no run is at work.
        mirror.review().decided().addListener((observed, was, now) -> {
            if (now != null) {
                log.debug("proofread standing: desk action {} on segment {}", now.action(), now.segmentId());
                refreshWhileNoRun(mirror, "desk action");
            }
        });
        mirror.runState().addListener((observed, was, now) -> {
            if (now.isSettled() || now == RunState.IDLE) {
                refresh();
            } else {
                tally.set(null);
                codeWork.set(false);
            }
        });
    }

    private void refreshWhileNoRun(final StateMirror mirror, final String cause) {
        final RunState state = mirror.runState().get();
        log.debug("proofread standing: {} in run state {}", cause, state);
        if (state.isSettled() || state == RunState.IDLE) {
            refresh();
        }
    }

    /**
     * The ports the standing reads.
     *
     * @param progress the planner of a stored project's proofread windows
     * @param batchSizes the reader of the chosen model's batch size
     */
    public record Ports(ProofreadProgress progress, ModelBatchSize batchSizes) {

        /** Rejects a missing port. */
        @Inject
        public Ports {
            Objects.requireNonNull(progress, "progress");
            Objects.requireNonNull(batchSizes, "batchSizes");
        }
    }

    /** {@return where the Proofread stage stands, null while it has no window left or nothing is known; FX thread} */
    public ReadOnlyObjectProperty<@Nullable ProofreadTally> tally() {
        return tally.getReadOnlyProperty();
    }

    /** {@return whether a Continue has code-only Proofread work left, false while a run is at work; FX thread} */
    public ReadOnlyBooleanProperty codeWorkLeft() {
        return codeWork.getReadOnlyProperty();
    }

    /** Reads the tally of the open project again; FX thread only. */
    public void refresh() {
        final @Nullable OpenedBook book = current.book().get();
        final long ask = ++asked;
        if (book == null) {
            tally.set(null);
            codeWork.set(false);
            return;
        }
        final @Nullable ModelSelection chosen = settings.selection().orElse(null);
        final String projectId = book.projectId();
        log.debug("proofread standing #{} asked for project {}, model chosen {}", ask, projectId, chosen != null);
        try {
            background.execute(() -> read(ask, projectId, chosen));
        } catch (RejectedExecutionException _) {
            // The window is closing and its executor is shut down: nothing is left to show the tally on.
            log.debug("proofread standing #{} not read: the background executor is shut down", ask);
        }
    }

    private void read(final long ask, final String projectId, final @Nullable ModelSelection chosen) {
        final @Nullable ProofreadTally now = known(ask, "tally", progress.tally(projectId, batchSizes.of(chosen)))
                .flatMap(found -> found)
                .orElse(null);
        final boolean work =
                known(ask, "code work", progress.codeWorkLeft(projectId)).orElse(false);
        log.debug("proofread standing #{} of project {}: {} codeWork={}", ask, projectId, now, work);
        fx.execute(() -> {
            if (ask == asked) {
                tally.set(now);
                codeWork.set(work);
            }
        });
    }

    // A read that failed shows nothing left; the port logged the failure with its cause.
    private static <T> Optional<T> known(final long ask, final String what, final Result<T> read) {
        final @Nullable AppError error = read.error();
        if (error != null) {
            log.warn(
                    "proofread standing #{}: the {} could not be read code={}; nothing is shown",
                    ask,
                    what,
                    error.code());
        }
        return Optional.ofNullable(read.data());
    }
}
