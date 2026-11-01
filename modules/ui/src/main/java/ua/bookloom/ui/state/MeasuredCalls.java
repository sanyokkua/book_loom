package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.CallLedger;
import ua.bookloom.api.llm.CallSeconds;
import ua.bookloom.api.llm.MeasuredFrom;
import ua.bookloom.api.llm.MeasuredSpeed;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.persistence.ModelSettingsRepository;
import ua.bookloom.api.pipeline.CallKind;

/**
 * The seconds per call of each kind on the chosen model: read for an estimate, and measured around a run or a names
 * pass from the call ledger and stored on the model when it ends. {@code :pipeline} holds no model-settings store and
 * no provider id, so the window does both ends here.
 */
@Slf4j
@Singleton
public final class MeasuredCalls {

    private final CallLedger ledger;
    private final Function<ModelSelection, Map<CallKind, MeasuredSpeed>> read;
    private final BiConsumer<ModelSelection, Map<CallKind, Double>> write;

    /**
     * Reads and stores through the model settings.
     *
     * @param ledger the application's call ledger
     * @param store where a model's stored seconds are read
     * @param recorder where measured seconds are stored
     */
    @Inject
    public MeasuredCalls(
            final CallLedger ledger, final ModelSettingsRepository store, final ModelSpeedRecorder recorder) {
        this(
                ledger,
                model -> stored(store, model),
                (model, seconds) ->
                        recorder.recordCalls(model.providerId(), model.modelId(), seconds, MeasuredFrom.RUN));
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(recorder, "recorder");
    }

    MeasuredCalls(
            final CallLedger ledger,
            final Function<ModelSelection, Map<CallKind, MeasuredSpeed>> read,
            final BiConsumer<ModelSelection, Map<CallKind, Double>> write) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.read = Objects.requireNonNull(read, "read");
        this.write = Objects.requireNonNull(write, "write");
    }

    /**
     * Seconds that are never read nor kept, for a screen or runner built without the injector.
     *
     * @return an instance whose reads are empty and whose stores do nothing
     */
    static MeasuredCalls none() {
        return new MeasuredCalls(CallLedger.none(), model -> Map.of(), (model, seconds) -> {});
    }

    /**
     * The stored seconds of the chosen model. Reads the store: call it off the FX thread.
     *
     * @param model the chosen model, or null when none is chosen
     * @return the seconds of each kind measured; never null, empty when none is chosen or nothing was measured
     */
    Map<CallKind, MeasuredSpeed> stored(@Nullable final ModelSelection model) {
        return model == null ? Map.of() : read.apply(model);
    }

    /**
     * Where the ledger stands before a run or a pass.
     *
     * @return the mark {@link #keep} counts from
     */
    long mark() {
        return ledger.mark();
    }

    /**
     * Stores the seconds per call of each kind the calls after a mark took on the model. Any thread.
     *
     * @param model the model the calls went to; null stores nothing
     * @param mark what {@link #mark()} returned before the work
     * @param what the work's name for the log, {@code run} or {@code names pass}
     */
    void keep(@Nullable final ModelSelection model, final long mark, final String what) {
        try {
            final Map<CallKind, Double> seconds = CallSeconds.of(ledger.since(mark));
            log.debug("{} measured seconds per call {} on {}", what, seconds, model);
            if (model != null) {
                write.accept(model, seconds);
            }
        } catch (RuntimeException failure) {
            log.warn("the seconds per call of the {} could not be kept; its end is unaffected", what, failure);
        }
    }

    private static Map<CallKind, MeasuredSpeed> stored(
            final ModelSettingsRepository store, final ModelSelection model) {
        return Optional.ofNullable(
                        store.find(model.providerId(), model.modelId()).data())
                .flatMap(found -> found)
                .map(row -> row.secondsPerCall())
                .orElse(Map.of());
    }
}
