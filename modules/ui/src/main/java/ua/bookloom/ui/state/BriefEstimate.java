package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.MeasuredSpeed;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.persistence.ModelSettingsRepository;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.DialProfile;
import ua.bookloom.api.pipeline.DialProfiles;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * The time estimate under the quality choice, by the one rule of {@link StartEstimates}: measured when a run stored
 * seconds for every kind of call the plan makes on the chosen model, else a range from its stored seconds per drafted
 * segment, else nothing. The chunks are the segments over the dial's chunk cap. Tokens per second are never an input,
 * so they alone give no estimate. It is worked out again when the dial, the model, the book's size or a stored
 * speed changes. The stored speed is read on the background executor, never on the FX thread; a read that a newer
 * one overtook is dropped. FX thread only, apart from that read.
 */
@Slf4j
@Singleton
public final class BriefEstimate {

    private final CurrentProject current;
    private final SettingsViewModel settings;
    private final BriefProject project;
    private final ModelSettingsRepository stored;
    private final DialProfiles profiles;
    private final Executor background;
    private final Executor fx;
    private long asked;
    private final ReadOnlyObjectWrapper<StartEstimate> estimate = new ReadOnlyObjectWrapper<>(new StartEstimate.None());

    /**
     * Follows the inputs of the estimate.
     *
     * @param current the open project, whose brief holds the dial
     * @param settings the chosen provider and model
     * @param project the open project's segment count
     * @param stored where the measured speed of a model is kept
     * @param speeds told when a speed was stored, so the estimate is worked out again
     * @param profiles what each quality does, which gives the chunks and the review passes the plan makes
     * @param background where the stored speed is read, never the FX thread
     */
    @Inject
    public BriefEstimate(
            final CurrentProject current,
            final SettingsViewModel settings,
            final BriefProject project,
            final ModelSettingsRepository stored,
            final ModelSpeeds speeds,
            final DialProfiles profiles,
            final @BackgroundExecutor ExecutorService background) {
        this(current, settings, project, stored, speeds, profiles, background, Platform::runLater);
    }

    /**
     * The same, with the hop back to the FX thread supplied.
     *
     * @param current the open project, whose brief holds the dial
     * @param settings the chosen provider and model
     * @param project the open project's segment count
     * @param stored where the measured speed of a model is kept
     * @param speeds told when a speed was stored, so the estimate is worked out again
     * @param profiles what each quality does
     * @param background where the stored speed is read
     * @param fx runs a task on the FX Application Thread
     */
    public BriefEstimate(
            final CurrentProject current,
            final SettingsViewModel settings,
            final BriefProject project,
            final ModelSettingsRepository stored,
            final ModelSpeeds speeds,
            final DialProfiles profiles,
            final Executor background,
            final Executor fx) {
        Objects.requireNonNull(speeds, "speeds");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.background = Objects.requireNonNull(background, "background");
        this.fx = Objects.requireNonNull(fx, "fx");
        this.current = Objects.requireNonNull(current, "current");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.project = Objects.requireNonNull(project, "project");
        this.stored = Objects.requireNonNull(stored, "stored");
        current.brief().addListener((observed, was, now) -> recompute());
        settings.model().addListener((observed, was, now) -> recompute());
        settings.selectedProviderId().addListener((observed, was, now) -> recompute());
        project.totalSegments().addListener((observed, was, now) -> recompute());
        speeds.revision().addListener((observed, was, now) -> recompute());
        recompute();
    }

    /** {@return the estimate to show; always holds one} */
    public ReadOnlyObjectProperty<StartEstimate> estimate() {
        return estimate.getReadOnlyProperty();
    }

    // The inputs are read here on the FX thread; the stored speed in the background; the answer back on the FX thread.
    private void recompute() {
        final @Nullable BookBrief brief = current.brief().get();
        final QualityDial dial = brief == null ? QualityDial.BALANCED : brief.dial();
        final Optional<ModelSelection> chosen = settings.selection();
        final int segments = project.totalSegments().get();
        final long ask = ++asked;
        log.debug("estimate #{} asked for {} segments on {}, model chosen {}", ask, segments, dial, chosen.isPresent());
        final DialProfile profile = profiles.of(dial);
        final int chunks = Math.ceilDiv(Math.max(0, segments), Math.max(1, profile.chunkCap()));
        background.execute(() -> {
            final StartEstimate now =
                    StartEstimates.of(speedsOf(chosen), segments, chunks, dial, profile.reviewPasses());
            fx.execute(() -> publish(ask, now));
        });
    }

    private void publish(final long ask, final StartEstimate now) {
        if (ask != asked) {
            log.debug("estimate #{} dropped: #{} was asked since", ask, asked);
            return;
        }
        log.debug("estimate #{}: {}", ask, now.getClass().getSimpleName());
        if (!now.equals(estimate.get())) {
            estimate.set(now);
        }
    }

    // A keyed read of one stored row, taken on the background executor.
    private Map<CallKind, MeasuredSpeed> speedsOf(final Optional<ModelSelection> chosen) {
        return chosen.flatMap(model -> Optional.ofNullable(
                                stored.find(model.providerId(), model.modelId()).data())
                        .flatMap(found -> found)
                        .map(ModelSettings::secondsPerCall))
                .orElse(Map.of());
    }
}
