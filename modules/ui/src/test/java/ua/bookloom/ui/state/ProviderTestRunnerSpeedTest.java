package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.MeasuredFrom;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.ScriptedProviderVerifier;

/** A passing inference test of a saved provider keeps the seconds one segment took; nothing else does. */
class ProviderTestRunnerSpeedTest extends ProviderFormTestBase {

    private static Result<VerificationReport> inferenceTook(final StageStatus status, final long millis) {
        final AppError error = status == StageStatus.FAILED
                ? AppError.of(ErrorCode.unreachable, "Unreachable", "The server did not answer.")
                : null;
        return Result.ok(new VerificationReport(java.util.List.of(
                new StageOutcome(
                        VerificationStage.CONNECTION, StageStatus.PASSED, null, null, Duration.ofMillis(41), null),
                new StageOutcome(VerificationStage.INFERENCE, status, error, null, Duration.ofMillis(millis), null))));
    }

    private void runInference(final SettingsViewModel viewModel) {
        onFx(() -> {
            viewModel.tests().run(ProviderTest.INFERENCE);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private ModelSettings storedFor(final String provider, final String model) {
        return Objects.requireNonNull(modelSettings.find(provider, model).data())
                .orElseThrow();
    }

    // IF the inference test stored no speed, THEN the estimate before Start could never name the model's pace.
    @Test
    void run_storedProviderInferencePasses_storesTheSecondsAndTheTestSource() throws TimeoutException {
        final SettingsViewModel viewModel =
                viewModel(ScriptedProviderVerifier.returning(inferenceTook(StageStatus.PASSED, 1234)));
        final ProviderForm form = form(viewModel);
        edit(form, draft -> draft.withModel("qwen3:8b"));
        final long before = onFx(() -> speeds.revision().get());

        runInference(viewModel);
        WaitForAsyncUtils.waitFor(
                5, TimeUnit.SECONDS, () -> onFx(() -> speeds.revision().get()) > before);

        assertThat(storedFor("ollama", "qwen3:8b").measuredSecondsPerSegment()).isEqualTo(1.2);
        assertThat(storedFor("ollama", "qwen3:8b").measuredFrom()).isEqualTo(MeasuredFrom.TEST);
    }

    // IF a failed inference stored a speed, THEN the estimate would be built on an answer the model never gave.
    @Test
    void run_inferenceFails_storesNothing() {
        final SettingsViewModel viewModel =
                viewModel(ScriptedProviderVerifier.returning(inferenceTook(StageStatus.FAILED, 900)));
        final ProviderForm form = form(viewModel);
        edit(form, draft -> draft.withModel("qwen3:8b"));

        runInference(viewModel);

        assertThat(modelSettings.saves()).isEmpty();
    }

    // IF a provider that is not saved got a speed, THEN a row would exist for a provider the registry does not know.
    @Test
    void run_newProviderPasses_storesNothing() {
        final SettingsViewModel viewModel =
                viewModel(ScriptedProviderVerifier.returning(inferenceTook(StageStatus.PASSED, 1234)));
        final ProviderForm form = form(viewModel);
        addOfficeLm(viewModel, form);
        edit(form, draft -> draft.withModel("qwen3:8b"));

        runInference(viewModel);

        assertThat(modelSettings.saves()).isEmpty();
    }

    // IF the shorter tests stored a speed, THEN a list read would be taken for a model's pace.
    @Test
    void run_modelsTestPasses_storesNothing() {
        final SettingsViewModel viewModel =
                viewModel(ScriptedProviderVerifier.returning(inferenceTook(StageStatus.PASSED, 1234)));
        final ProviderForm form = form(viewModel);
        edit(form, draft -> draft.withModel("qwen3:8b"));
        onFx(() -> {
            viewModel.tests().run(ProviderTest.MODELS);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(modelSettings.saves()).isEmpty();
    }

    // IF an address typed but not saved got a speed, THEN the row would hold a pace measured on another server.
    @Test
    void run_addressEditedNotSaved_storesNothing() {
        final SettingsViewModel viewModel =
                viewModel(ScriptedProviderVerifier.returning(inferenceTook(StageStatus.PASSED, 1234)));
        final ProviderForm form = form(viewModel);
        edit(form, draft -> draft.withModel("qwen3:8b").withAddress("http://10.0.0.5:11434"));

        runInference(viewModel);

        assertThat(modelSettings.saves()).isEmpty();
    }
}
