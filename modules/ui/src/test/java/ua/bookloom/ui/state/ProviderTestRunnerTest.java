package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.ScriptedProviderVerifier;
import ua.bookloom.ui.i18n.MessageKey;

/** The three provider tests: what each asks, when each is available, and the findings each report becomes. */
class ProviderTestRunnerTest extends SettingsViewModelTestBase {

    private final StringProperty provider = new SimpleStringProperty("ollama");
    private final StringProperty model = new SimpleStringProperty("");

    private ProviderTestRunner runner(final ScriptedProviderVerifier verifier) {
        return onFx(() -> new ProviderTestRunner(verifier, toasts, errors, executor, provider, model, activities));
    }

    private void setModel(final String text) {
        onFx(() -> {
            model.set(text);
            return null;
        });
    }

    private void run(final ProviderTestRunner runner, final ProviderTest test) {
        onFx(() -> {
            runner.run(test);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static StageOutcome measured(
            final VerificationStage stage, final StageStatus status, final long millis, final @Nullable Integer count) {
        return new StageOutcome(stage, status, null, null, Duration.ofMillis(millis), count);
    }

    private static List<StageChip> chips(final ProviderTestRunner runner) {
        return onFx(() -> List.copyOf(runner.stages()));
    }

    private static boolean available(final ProviderTestRunner runner, final ProviderTest test) {
        return onFx(() -> runner.available(test).get());
    }

    // IF the connection test waited for a model, THEN nobody could ask whether anything is listening before choosing.
    @Test
    void available_noModel_onlyTheConnectionTestIsAvailableAndChoosingEnablesTheRest() {
        final ProviderTestRunner runner = runner(ScriptedProviderVerifier.idle());

        assertThat(available(runner, ProviderTest.CONNECTION)).isTrue();
        assertThat(available(runner, ProviderTest.MODELS)).isFalse();
        assertThat(available(runner, ProviderTest.INFERENCE)).isFalse();

        setModel("gemma3:12b");

        assertThat(available(runner, ProviderTest.MODELS)).isTrue();
        assertThat(available(runner, ProviderTest.INFERENCE)).isTrue();
    }

    // IF Test connection went through verify, THEN it would need a model and would ask about one.
    @Test
    void run_connection_asksTheConnectionOnlyCallAndReportsTheRoundTrip() {
        final ScriptedProviderVerifier verifier = ScriptedProviderVerifier.returning(Result.ok(
                new VerificationReport(List.of(measured(VerificationStage.CONNECTION, StageStatus.PASSED, 41, null)))));
        final ProviderTestRunner runner = runner(verifier);

        run(runner, ProviderTest.CONNECTION);

        assertThat(verifier.connectionCalls()).containsExactly("ollama");
        assertThat(verifier.selections()).isEmpty();
        assertThat(chips(runner))
                .extracting(StageChip::stage, StageChip::status, StageChip::elapsed)
                .containsExactly(tuple(VerificationStage.CONNECTION, StageStatus.PASSED, Duration.ofMillis(41)));
    }

    // IF Test models asked for more or less than connection and models, THEN it would cost an inference or miss the
    // list.
    @Test
    void run_models_asksForConnectionAndModelsAndReportsTwoFindings() {
        final ScriptedProviderVerifier verifier =
                ScriptedProviderVerifier.returning(Result.ok(new VerificationReport(List.of(
                        measured(VerificationStage.CONNECTION, StageStatus.PASSED, 41, null),
                        measured(VerificationStage.MODELS, StageStatus.PASSED, 5, 3)))));
        final ProviderTestRunner runner = runner(verifier);
        setModel("gemma3:12b");

        run(runner, ProviderTest.MODELS);

        assertThat(verifier.policies()).containsExactly(VerificationPolicy.CONNECTION_AND_MODELS);
        assertThat(verifier.selections()).containsExactly(new ModelSelection("ollama", "gemma3:12b"));
        assertThat(verifier.connectionCalls()).isEmpty();
        assertThat(chips(runner))
                .extracting(StageChip::stage, StageChip::count)
                .containsExactly(tuple(VerificationStage.CONNECTION, null), tuple(VerificationStage.MODELS, 3));
    }

    // IF a failed first stage were shown with its successors as passed or left out, THEN the report would misstate
    // what was attempted.
    @Test
    void run_inferenceNothingListening_unreachableThenTwoNotAttempted() {
        final AppError unreachable = AppError.of(ErrorCode.unreachable, "Unreachable", "The server did not answer.");
        final ProviderTestRunner runner = runner(ScriptedProviderVerifier.returning(Result.ok(new VerificationReport(
                List.of(new StageOutcome(VerificationStage.CONNECTION, StageStatus.FAILED, unreachable, null))))));
        setModel("gemma3:12b");

        run(runner, ProviderTest.INFERENCE);

        assertThat(chips(runner))
                .extracting(StageChip::stage, StageChip::status)
                .containsExactly(
                        tuple(VerificationStage.CONNECTION, StageStatus.FAILED),
                        tuple(VerificationStage.MODELS, StageStatus.SKIPPED),
                        tuple(VerificationStage.INFERENCE, StageStatus.SKIPPED));
        assertThat(chips(runner).getFirst().error()).isSameAs(unreachable);
        assertThat(toasts.raised()).isEmpty();
    }

    // IF a fully passing run raised no success notice or more than one, THEN the person would miss it or be nagged.
    @Test
    void run_inferenceAllPass_reportsThreeMeasuredValuesAndOneSuccessToast() {
        final ProviderTestRunner runner =
                runner(ScriptedProviderVerifier.returning(Result.ok(new VerificationReport(List.of(
                        measured(VerificationStage.CONNECTION, StageStatus.PASSED, 41, null),
                        measured(VerificationStage.MODELS, StageStatus.PASSED, 5, 3),
                        measured(VerificationStage.INFERENCE, StageStatus.PASSED, 1200, null))))));
        setModel("gemma3:12b");

        run(runner, ProviderTest.INFERENCE);

        assertThat(chips(runner))
                .extracting(StageChip::elapsed, StageChip::count)
                .containsExactly(
                        tuple(Duration.ofMillis(41), null),
                        tuple(Duration.ofMillis(5), 3),
                        tuple(Duration.ofMillis(1200), null));
        assertThat(toasts.raised()).hasSize(1);
        assertThat(toasts.raised().getFirst().key()).isEqualTo(MessageKey.TOAST_PROVIDER_PASSED);
    }

    // IF an unreadable listing stopped the run or counted as a failure, THEN a server with no listing endpoint, which
    // works, could never be tested to the end.
    @Test
    void run_inferenceUnreadableListing_qualifiedPassAndInferenceStillReported() {
        final AppError discovery = AppError.of(ErrorCode.discoveryFailed, "Discovery failed", "No list.");
        final ProviderTestRunner runner =
                runner(ScriptedProviderVerifier.returning(Result.ok(new VerificationReport(List.of(
                        measured(VerificationStage.CONNECTION, StageStatus.PASSED, 41, null),
                        new StageOutcome(
                                VerificationStage.MODELS,
                                StageStatus.SOFT_PASS,
                                discovery,
                                "model list unavailable",
                                Duration.ofMillis(5),
                                0),
                        measured(VerificationStage.INFERENCE, StageStatus.PASSED, 900, null))))));
        setModel("gemma3:12b");

        run(runner, ProviderTest.INFERENCE);

        assertThat(chips(runner))
                .extracting(StageChip::status)
                .containsExactly(StageStatus.PASSED, StageStatus.SOFT_PASS, StageStatus.PASSED);
        assertThat(chips(runner).get(1).note()).isEqualTo("model list unavailable");
    }

    // IF a second test could start while one is in flight, THEN two reports would race for one set of findings.
    @Test
    void run_inFlight_allThreeUnavailableUntilTheReportArrives() throws InterruptedException, TimeoutException {
        final ScriptedProviderVerifier verifier = ScriptedProviderVerifier.gated(allPassed());
        useCountingPool();
        final ProviderTestRunner runner = runner(verifier);
        setModel("gemma3:12b");

        onFx(() -> {
            runner.run(ProviderTest.INFERENCE);
            return null;
        });
        verifier.awaitEntered();

        assertThat(onFx(() -> runner.checking().get())).isTrue();
        assertThat(available(runner, ProviderTest.CONNECTION)).isFalse();
        assertThat(available(runner, ProviderTest.MODELS)).isFalse();
        assertThat(available(runner, ProviderTest.INFERENCE)).isFalse();

        verifier.release();
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> !onFx(() -> runner.checking().get()));

        assertThat(available(runner, ProviderTest.CONNECTION)).isTrue();
        assertThat(available(runner, ProviderTest.INFERENCE)).isTrue();
        assertThat(chips(runner)).hasSize(3);
    }

    // IF a report survived the model changing under it, THEN a verdict on the old model would sit against the new one.
    @Test
    void run_modelChangedWhileInFlight_reportIsDiscarded() throws InterruptedException {
        final ScriptedProviderVerifier verifier = ScriptedProviderVerifier.gated(allPassed());
        final CountingPool pool = useCountingPool();
        final ProviderTestRunner runner = runner(verifier);
        setModel("gemma3:12b");
        onFx(() -> {
            runner.run(ProviderTest.INFERENCE);
            return null;
        });
        verifier.awaitEntered();

        setModel("qwen3:8b");
        verifier.release();
        pool.awaitFinished(1);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(chips(runner)).isEmpty();
        assertThat(toasts.raised()).isEmpty();
        assertThat(onFx(() -> runner.checking().get())).isFalse();
    }

    // IF a model test ran with no model chosen, THEN the verifier would be asked about nothing.
    @Test
    void run_modelsWithNoModel_doesNothing() {
        final ScriptedProviderVerifier verifier = ScriptedProviderVerifier.idle();
        final ProviderTestRunner runner = runner(verifier);

        run(runner, ProviderTest.MODELS);

        assertThat(verifier.callCount()).isZero();
        assertThat(onFx(() -> runner.checking().get())).isFalse();
    }
}
