package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyLongProperty;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.MeasuredFrom;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.persistence.ModelSettingsRepository;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * Keeps the seconds per segment a test or a run measured on the model, and the seconds per call of each kind a run or a
 * names pass measured, for the estimates before Start and before the names pass.
 */
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

    /** {@return a count that grows each time this recorder stored a speed; FX thread only} */
    ReadOnlyLongProperty storedChanges() {
        return speeds.revision();
    }

    /**
     * Stores the time one segment took as the model's speed, replacing an earlier measurement of either source; every
     * other setting of the model, the refusals included, stays as the store holds it at that moment. Returns at once;
     * the store is called off the FX thread and the estimates are told on it. Any thread.
     *
     * @param providerId the stored provider
     * @param modelId the model
     * @param kind the call kind the time belongs to
     * @param elapsed how long one segment took
     * @param from where it was measured
     */
    public void record(
            final String providerId,
            final String modelId,
            final CallKind kind,
            final Duration elapsed,
            final MeasuredFrom from) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(elapsed, "elapsed");
        Objects.requireNonNull(from, "from");
        final double seconds = secondsOf(elapsed);
        log.debug("recording {} s for {} from {} for {}/{}", seconds, kind, from, providerId, modelId);
        try {
            executor.execute(() -> write(providerId, modelId, kind, seconds, from));
        } catch (RejectedExecutionException closing) {
            log.warn("the speed of {}/{} was not kept: the window is closing", providerId, modelId);
        }
    }

    /**
     * Stores the seconds per call of each kind a run or a names pass measured, in one update of the model's settings: a
     * kind not in the map keeps its stored figure. Returns at once; the store is called off the FX thread. Any thread.
     *
     * @param providerId the stored provider
     * @param modelId the model
     * @param seconds the seconds per call of each kind measured; an empty map stores nothing
     * @param from where they were measured
     */
    public void recordCalls(
            final String providerId,
            final String modelId,
            final Map<CallKind, Double> seconds,
            final MeasuredFrom from) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(seconds, "seconds");
        Objects.requireNonNull(from, "from");
        if (seconds.isEmpty()) {
            log.debug("no call of a stored kind was measured for {}/{}: nothing stored", providerId, modelId);
            return;
        }
        final Map<CallKind, Double> copy = Map.copyOf(seconds);
        log.debug("recording seconds per call {} from {} for {}/{}", copy, from, providerId, modelId);
        try {
            executor.execute(() -> writeAll(providerId, modelId, copy, from));
        } catch (RejectedExecutionException closing) {
            log.warn("the seconds per call of {}/{} were not kept: the window is closing", providerId, modelId);
        }
    }

    private void writeAll(
            final String providerId,
            final String modelId,
            final Map<CallKind, Double> seconds,
            final MeasuredFrom from) {
        final Result<ModelSettings> saved = store.update(providerId, modelId, row -> {
            ModelSettings next = row;
            for (final Map.Entry<CallKind, Double> kind : seconds.entrySet()) {
                next = next.withMeasuredSpeed(kind.getKey(), kind.getValue(), from);
            }
            return next;
        });
        if (saved.isErr()) {
            log.warn("the seconds per call of {}/{} were not stored", providerId, modelId);
            return;
        }
        log.info("seconds per call of {}/{} stored: {} from {}", providerId, modelId, seconds, from);
        Platform.runLater(speeds::bump);
    }

    static double secondsOf(final Duration elapsed) {
        final double tenths = Math.round(elapsed.toMillis() / MILLIS_PER_TENTH);
        return Math.max(MIN_SECONDS, tenths / TENTHS_PER_SECOND);
    }

    private void write(
            final String providerId,
            final String modelId,
            final CallKind kind,
            final double seconds,
            final MeasuredFrom from) {
        final Result<ModelSettings> saved =
                store.update(providerId, modelId, row -> row.withMeasuredSpeed(kind, seconds, from));
        if (saved.isErr()) {
            log.warn("the speed of {}/{} was not stored", providerId, modelId);
            return;
        }
        log.info("speed of {}/{} stored: {} s for {} from {}", providerId, modelId, seconds, kind, from);
        Platform.runLater(speeds::bump);
    }
}
