package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationExchange;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.ScriptedProviderVerifier;

/** A passing inference test of a saved provider notes whether the model answered in the structured form. */
class ProviderTestRunnerStructuredTest extends ProviderFormTestBase {

    private static Result<VerificationReport> inference(final StageStatus status, final boolean structured) {
        final AppError error = status == StageStatus.FAILED
                ? AppError.of(ErrorCode.unreachable, "Unreachable", "The server did not answer.")
                : null;
        final VerificationExchange exchange =
                new VerificationExchange("POST", "http://localhost/chat", "{}", "{}", 200, null, null, structured);
        return Result.ok(new VerificationReport(List.of(new StageOutcome(
                VerificationStage.INFERENCE, status, error, null, Duration.ofMillis(900), null, exchange))));
    }

    private void runInference(final SettingsViewModel viewModel) {
        onFx(() -> {
            viewModel.tests().run(ProviderTest.INFERENCE);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF a pass were not noted, THEN the Models tab could never say whether the model answers in the structured form.
    @Test
    void run_inferencePassedStoredProvider_recordsStructured() {
        final StructuredAnswers answers = new StructuredAnswers();
        final SettingsViewModel viewModel =
                viewModel(ScriptedProviderVerifier.returning(inference(StageStatus.PASSED, true)), answers);
        edit(form(viewModel), draft -> draft.withModel("qwen3:8b"));

        runInference(viewModel);

        assertThat(onFx(() -> answers.lookup("ollama", "qwen3:8b"))).contains(true);
    }

    // IF a provider that is not saved were noted, THEN a model of a server the registry never held would show an
    // answer.
    @Test
    void run_newProvider_recordsNothing() {
        final StructuredAnswers answers = new StructuredAnswers();
        final SettingsViewModel viewModel =
                viewModel(ScriptedProviderVerifier.returning(inference(StageStatus.PASSED, true)), answers);
        final ProviderForm form = form(viewModel);
        addOfficeLm(viewModel, form);
        edit(form, draft -> draft.withModel("qwen3:8b"));

        runInference(viewModel);

        assertThat(onFx(() -> answers.revision().get())).isZero();
    }

    // IF a failed test were noted, THEN "no" would be shown for a model that never answered.
    @Test
    void run_failedInference_recordsNothing() {
        final StructuredAnswers answers = new StructuredAnswers();
        final SettingsViewModel viewModel =
                viewModel(ScriptedProviderVerifier.returning(inference(StageStatus.FAILED, false)), answers);
        edit(form(viewModel), draft -> draft.withModel("qwen3:8b"));

        runInference(viewModel);

        assertThat(onFx(() -> answers.revision().get())).isZero();
    }
}
