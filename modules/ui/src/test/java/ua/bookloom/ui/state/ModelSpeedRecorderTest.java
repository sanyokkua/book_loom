package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.llm.MeasuredFrom;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.RefusedSetting;
import ua.bookloom.api.llm.TemperatureChoice;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ScriptedModelSettings;

/** What a measured speed does to the stored settings of a model: only the speed and its source change. */
class ModelSpeedRecorderTest extends FxTestBase {

    private static final long WAIT_SECONDS = 10;
    private static final RefusedSetting REFUSED =
            new RefusedSetting("temperature", LocalDate.of(2026, 10, 1), "left it to the server");

    private final ScriptedModelSettings store = new ScriptedModelSettings();
    private final ModelSpeeds speeds = new ModelSpeeds();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ModelSpeedRecorder recorder = new ModelSpeedRecorder(store, speeds, executor);

    @Override
    public void start(final Stage stage) {
        // The toolkit is all this needs.
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    private void record(final long millis, final MeasuredFrom from) throws TimeoutException {
        final long before = onFx(() -> speeds.revision().get());
        onFx(() -> {
            recorder.record("ollama", "gemma", Duration.ofMillis(millis), from);
            return null;
        });
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> onFx(() -> speeds.revision().get()) > before);
    }

    private ModelSettings stored() {
        return Objects.requireNonNull(store.find("ollama", "gemma").data()).orElseThrow();
    }

    // IF the stored speed were not rounded to a tenth of a second, THEN the estimate would show false precision.
    @Test
    void record_testMeasurement_storesTenthsAndTheTestSource() throws TimeoutException {
        record(1234, MeasuredFrom.TEST);

        assertThat(stored().measuredSecondsPerSegment()).isEqualTo(1.2);
        assertThat(stored().measuredFrom()).isEqualTo(MeasuredFrom.TEST);
    }

    // IF a zero elapsed time were stored, THEN the settings record would refuse it and the speed would be lost.
    @Test
    void record_zeroElapsed_storesATenthOfASecond() throws TimeoutException {
        record(0, MeasuredFrom.TEST);

        assertThat(stored().measuredSecondsPerSegment()).isEqualTo(0.1);
    }

    // IF the last measurement did not win, THEN an old test would outlive a translation that is more telling.
    @Test
    void record_runAfterTest_overwritesTheSource() throws TimeoutException {
        store.measured("ollama", "gemma", 1.2, MeasuredFrom.TEST);

        record(2000, MeasuredFrom.RUN);

        assertThat(stored().measuredSecondsPerSegment()).isEqualTo(2.0);
        assertThat(stored().measuredFrom()).isEqualTo(MeasuredFrom.RUN);
    }

    // IF a refusal recorded earlier were dropped, THEN the model would be sent a setting it refused.
    @Test
    void record_modelWithRefusalsAndTemperature_keepsEverythingElse() throws TimeoutException {
        store.save(new ModelSettings(
                "ollama",
                "gemma",
                TemperatureChoice.VALUE,
                0.7,
                8192,
                1024,
                ModelSettings.defaults("ollama", "gemma").outputField(),
                List.of(REFUSED),
                null,
                null));

        record(1234, MeasuredFrom.TEST);

        assertThat(stored().refused()).containsExactly(REFUSED);
        assertThat(stored().temperature()).isEqualTo(0.7);
        assertThat(stored().contextWindow()).isEqualTo(8192);
        assertThat(stored().maxOutput()).isEqualTo(1024);
    }

    // IF the speed replaced the whole row, THEN a refusal stored just before it would be lost; the store applies the
    // change to what it holds (its atomicity against a concurrent writer is the repository contract test's).
    @Test
    void record_refusalStoredJustBefore_isKept() throws TimeoutException {
        store.duringFind(() -> store.save(refusing()));

        record(1234, MeasuredFrom.TEST);

        assertThat(stored().refused()).containsExactly(REFUSED);
    }

    private static ModelSettings refusing() {
        final ModelSettings base = ModelSettings.defaults("ollama", "gemma");
        return new ModelSettings(
                "ollama",
                "gemma",
                base.temperatureChoice(),
                null,
                null,
                null,
                base.outputField(),
                List.of(REFUSED),
                null,
                null);
    }

    // IF the store were touched on the FX thread, THEN the window would stall on a slow disk.
    @Test
    void record_anyMeasurement_neverTouchesTheStoreOnTheFxThread() throws TimeoutException {
        record(1234, MeasuredFrom.TEST);

        assertThat(store.fxThreadCalls()).isZero();
        assertThat(stored().modelId()).isEqualTo("gemma");
    }
}
