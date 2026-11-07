package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.ProofreadProgress;
import ua.bookloom.api.pipeline.ProofreadTally;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * Where the open project's Proofread stage stands while no run is at work, so a reopened or stopped project says
 * "Continues with Proofread at window 41 of 120." (design D15 "Resume"). It is read in the background, with no model
 * call, whenever the open book changes or a run settles; the windows are planned with the chosen model's batch size, as
 * the run will plan them, or the built-in one when no model is chosen. A read that a newer one overtook is dropped.
 * FX thread only, apart from that read.
 */
@Slf4j
@Singleton
public final class ProofreadStanding {

    private final ProofreadProgress progress;
    private final ChatModelFactory models;
    private final SettingsViewModel settings;
    private final CurrentProject current;
    private final Executor background;
    private final Executor fx;
    private final ReadOnlyObjectWrapper<@Nullable ProofreadTally> tally = new ReadOnlyObjectWrapper<>();
    private long asked;

    /**
     * Follows the open project and the run's state.
     *
     * @param ports the planner of the windows and the factory the chosen model's batch size is read from
     * @param settings the chosen model
     * @param current the open project
     * @param mirror the run's state, whose settling reads the tally again
     * @param background where the windows are planned, never the FX thread
     */
    @Inject
    public ProofreadStanding(
            final Ports ports,
            final SettingsViewModel settings,
            final CurrentProject current,
            final StateMirror mirror,
            final @BackgroundExecutor ExecutorService background) {
        this(ports, settings, current, mirror, background, Platform::runLater);
    }

    ProofreadStanding(
            final Ports ports,
            final SettingsViewModel settings,
            final CurrentProject current,
            final StateMirror mirror,
            final Executor background,
            final Executor fx) {
        this.progress = Objects.requireNonNull(ports, "ports").progress();
        this.models = ports.models();
        this.settings = Objects.requireNonNull(settings, "settings");
        this.current = Objects.requireNonNull(current, "current");
        this.background = Objects.requireNonNull(background, "background");
        this.fx = Objects.requireNonNull(fx, "fx");
        current.book().addListener((observed, was, now) -> refresh());
        mirror.runState().addListener((observed, was, now) -> {
            if (now.isSettled() || now == RunState.IDLE) {
                refresh();
            } else if (tally.get() != null) {
                tally.set(null);
            }
        });
    }

    /**
     * The ports the standing reads.
     *
     * @param progress the planner of a stored project's proofread windows
     * @param models the factory the chosen model's profile, and so its batch size, comes from
     */
    public record Ports(ProofreadProgress progress, ChatModelFactory models) {

        /** Rejects a missing port. */
        @Inject
        public Ports {
            Objects.requireNonNull(progress, "progress");
            Objects.requireNonNull(models, "models");
        }
    }

    /** {@return where the Proofread stage stands, null while it has no window left or nothing is known; FX thread} */
    public ReadOnlyObjectProperty<@Nullable ProofreadTally> tally() {
        return tally.getReadOnlyProperty();
    }

    /** Reads the tally of the open project again; FX thread only. */
    public void refresh() {
        final @Nullable OpenedBook book = current.book().get();
        final long ask = ++asked;
        if (book == null) {
            tally.set(null);
            return;
        }
        final Optional<ModelSelection> chosen = settings.selection();
        final String projectId = book.projectId();
        log.debug("proofread standing #{} asked for project {}, model chosen {}", ask, projectId, chosen.isPresent());
        try {
            background.execute(() -> read(ask, projectId, chosen));
        } catch (RejectedExecutionException _) {
            // The window is closing and its executor is shut down: nothing is left to show the tally on.
            log.debug("proofread standing #{} not read: the background executor is shut down", ask);
        }
    }

    private void read(final long ask, final String projectId, final Optional<ModelSelection> chosen) {
        final Result<Optional<ProofreadTally>> read = progress.tally(projectId, batchSize(chosen));
        final @Nullable ProofreadTally now =
                Optional.ofNullable(read.data()).flatMap(found -> found).orElse(null);
        log.debug("proofread standing #{} of project {}: {}", ask, projectId, now);
        fx.execute(() -> {
            if (ask == asked) {
                tally.set(now);
            }
        });
    }

    // Binding a model makes no call; a choice that cannot be bound plans with the built-in batch size.
    private int batchSize(final Optional<ModelSelection> chosen) {
        return chosen.map(models::create)
                .map(Result::data)
                .map(ChatModel::profile)
                .map(ModelProfile::batchSize)
                .orElse(ModelProfile.DEFAULT_BATCH_SIZE);
    }
}
