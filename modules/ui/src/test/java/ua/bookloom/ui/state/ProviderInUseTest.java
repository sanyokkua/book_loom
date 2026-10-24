package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleStringProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;

/** A provider the translation run uses, running or paused, counts as in use; any other provider does not. */
class ProviderInUseTest extends RunnerTestBase {

    // IF a paused run's provider could be deleted, THEN continuing or retrying would reach for a provider that is gone.
    @ParameterizedTest
    @CsvSource({"pseudo, true", "ollama, false"})
    void byRun_runPaused_isTrueOnlyForTheRunsProvider(final String providerId, final boolean expected)
            throws Exception {
        startJob();
        awaitState(RunState.RUNNING);
        job.emit(new Paused(PauseReason.REQUESTED, null, progress(2, 0, 7)));
        job.drain();
        awaitState(RunState.PAUSED);

        assertThat(inUse(providerId)).isEqualTo(expected);
    }

    @Test
    void byRun_noRun_isFalse() {
        assertThat(inUse("pseudo")).isFalse();
    }

    @Test
    void byRun_runFinished_isFalse() throws Exception {
        startJob();
        awaitState(RunState.RUNNING);
        job.finish(Result.ok(completedReport(3)));
        awaitState(RunState.COMPLETED);

        assertThat(inUse("pseudo")).isFalse();
    }

    private boolean inUse(final String providerId) {
        return onFx(() -> {
            final BooleanBinding binding =
                    new ProviderInUse(mirror, runner).byRun(new SimpleStringProperty(providerId));
            return binding.get();
        });
    }
}
