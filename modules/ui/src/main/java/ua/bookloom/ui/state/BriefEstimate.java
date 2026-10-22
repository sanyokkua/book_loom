package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.persistence.ModelSettingsRepository;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;

/**
 * The time estimate under the quality choice: a time only when the model test on this computer stored a speed for the
 * chosen model, scaled by the dial and the book's segments, else the relative costs. Tokens per second are never an
 * input, so they alone give no estimate. It is worked out again when the dial, the model or the book's size changes.
 * FX thread only.
 */
@Slf4j
@Singleton
public final class BriefEstimate {

    private final CurrentProject current;
    private final SettingsViewModel settings;
    private final BriefProject project;
    private final ModelSettingsRepository stored;
    private final ReadOnlyObjectWrapper<StartEstimate> estimate =
            new ReadOnlyObjectWrapper<>(new StartEstimate.Relative());

    /**
     * Follows the inputs of the estimate.
     *
     * @param current the open project, whose brief holds the dial
     * @param settings the chosen provider and model
     * @param project the open project's segment count
     * @param stored where the measured speed of a model is kept
     */
    @Inject
    public BriefEstimate(
            final CurrentProject current,
            final SettingsViewModel settings,
            final BriefProject project,
            final ModelSettingsRepository stored) {
        this.current = Objects.requireNonNull(current, "current");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.project = Objects.requireNonNull(project, "project");
        this.stored = Objects.requireNonNull(stored, "stored");
        current.brief().addListener((observed, was, now) -> recompute());
        settings.model().addListener((observed, was, now) -> recompute());
        settings.selectedProviderId().addListener((observed, was, now) -> recompute());
        project.totalSegments().addListener((observed, was, now) -> recompute());
        recompute();
    }

    /** {@return the estimate to show; always holds one} */
    public ReadOnlyObjectProperty<StartEstimate> estimate() {
        return estimate.getReadOnlyProperty();
    }

    private void recompute() {
        final @Nullable BookBrief brief = current.brief().get();
        final QualityDial dial = brief == null ? QualityDial.BALANCED : brief.dial();
        final Optional<ModelSelection> chosen = settings.selection();
        final StartEstimate now =
                StartEstimates.of(() -> speedOf(chosen), project.totalSegments().get(), dial);
        log.debug(
                "estimate for {} segments on {}: {}",
                project.totalSegments().get(),
                dial,
                now.getClass().getSimpleName());
        if (!now.equals(estimate.get())) {
            estimate.set(now);
        }
    }

    // A keyed read of one stored row, cheap enough to take on the FX thread.
    private Optional<Double> speedOf(final Optional<ModelSelection> chosen) {
        return chosen.flatMap(model -> Optional.ofNullable(
                        stored.find(model.providerId(), model.modelId()).data())
                .flatMap(found -> found)
                .map(ModelSettings::measuredSecondsPerSegment));
    }
}
