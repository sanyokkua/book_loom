package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.MeasuredFrom;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.persistence.ModelSettingsRepository;
import ua.bookloom.ui.BackgroundExecutor;

/** Keeps the seconds per segment a test or a run measured on the model, for the estimate before Start. */
@Slf4j
@Singleton
public final class ModelSpeedRecorder {

    private static final double MILLIS_PER_TENTH = 100.0;
    private static final double TENTHS_PER_SECOND = 10.0;
    private static final double MIN_SECONDS = 0.1;

    private final ModelSettingsRepository store;
    private final ModelSpeeds speeds;
    private final ExecutorService executor;

    /**
     * Creates the recorder.
     *
     * @param store where the model's settings are kept
     * @param speeds told when a speed changed
     * @param executor the daemon executor the store is called on
     */
    @Inject
    public ModelSpeedRecorder(
            final ModelSettingsRepository store,
            final ModelSpeeds speeds,
            @BackgroundExecutor final ExecutorService executor) {
        this.store = Objects.requireNonNull(store, "store");
        this.speeds = Objects.requireNonNull(speeds, "speeds");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Stores the time one segment took as the model's speed, replacing an earlier measurement of either source; every
     * other setting of the model, the refusals included, stays as the store holds it at that moment. Returns at once;
     * the store is called off the FX thread and the estimates are told on it. Any thread.
     *
     * @param providerId the stored provider
     * @param modelId the model
     * @param elapsed how long one segment took
     * @param from where it was measured
     */
    public void record(final String providerId, final String modelId, final Duration elapsed, final MeasuredFrom from) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(elapsed, "elapsed");
        Objects.requireNonNull(from, "from");
        final double seconds = secondsOf(elapsed);
        log.debug("recording {} s per segment from {} for {}/{}", seconds, from, providerId, modelId);
        try {
            executor.execute(() -> write(providerId, modelId, seconds, from));
        } catch (RejectedExecutionException closing) {
            log.warn("the speed of {}/{} was not kept: the window is closing", providerId, modelId);
        }
    }

    static double secondsOf(final Duration elapsed) {
        final double tenths = Math.round(elapsed.toMillis() / MILLIS_PER_TENTH);
        return Math.max(MIN_SECONDS, tenths / TENTHS_PER_SECOND);
    }

    private void write(final String providerId, final String modelId, final double seconds, final MeasuredFrom from) {
        final Result<ModelSettings> saved =
                store.update(providerId, modelId, row -> row.withMeasuredSpeed(seconds, from));
        if (saved.isErr()) {
            log.warn("the speed of {}/{} was not stored", providerId, modelId);
            return;
        }
        log.info("speed of {}/{} stored: {} s per segment from {}", providerId, modelId, seconds, from);
        Platform.runLater(speeds::bump);
    }
}
