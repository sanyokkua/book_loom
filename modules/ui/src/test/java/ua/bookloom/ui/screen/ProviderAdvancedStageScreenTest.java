package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Labeled;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationExchange;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.ProviderReports;
import ua.bookloom.ui.ThemeTestSupport;

/** The optional Advanced features stage on the Providers checks and in the test dialog, and the route line. */
class ProviderAdvancedStageScreenTest extends SettingsScreenTestBase {

    private static final String GENERIC_NOTE =
            "schema=honoured; thinking=reasoning_effort; samplers=ignored; cap=max_tokens";

    private void runAdvanced(final Result<VerificationReport> report) throws TimeoutException {
        verifier.respondWith(report);
        onFx(() -> viewModel().draftModel().set(MODEL));
        onFx(() -> button("settings-test-advanced").fire());
        awaitFx(() -> !viewModel().tests().checking().get()
                && viewModel().tests().stages().size() == 4);
    }

    private String shown(final String id) {
        return ThemeTestSupport.onFx(() -> ((Labeled) required(id)).getText());
    }

    // Before it runs the row says what the stage is: optional, and it uses the model.
    @Test
    void advancedStage_notRun_saysItIsOptionalAndUsesTheModel() {
        openSettings();

        assertThat(shown("check-result-ADVANCED")).isEqualTo("Advanced features (optional, uses the model)");
    }

    // IF the button asked another depth, THEN the verdicts would never be found or the other stages would send them.
    @Test
    void advancedStage_pressed_asksTheAdvancedDepthForTheModel() throws TimeoutException {
        openSettings();

        runAdvanced(ProviderReports.advancedPassed(GENERIC_NOTE));

        assertThat(verifier.policies()).containsExactly(VerificationPolicy.ADVANCED);
        assertThat(verifier.drafts()).extracting(call -> call.modelId()).containsExactly(MODEL);
    }

    // A generic server runs all four checks; each is one line, in the person's words, not the verifier's.
    @Test
    void advancedStage_genericServer_showsOneVerdictLinePerCheck() throws TimeoutException {
        openSettings();

        runAdvanced(ProviderReports.advancedPassed(GENERIC_NOTE));

        assertThat(shown("check-result-ADVANCED"))
                .isEqualTo("✓ Structured answers: honoured\nTurning reasoning off: works with reasoning_effort\n"
                        + "top_k/min_p: ignored\nAnswer cap: max_tokens");
    }

    // The dialog names the stage and lists one figure per check; a recognised engine runs two checks, so two lines.
    @Test
    void advancedDialog_recognisedEngine_listsTwoVerdicts() throws TimeoutException {
        openSettings();
        runAdvanced(ProviderReports.advancedPassed("schema=honoured; thinking=think"));

        onFx(() -> ((Hyperlink) required("check-details-ADVANCED")).fire());

        assertThat(ThemeTestSupport.onFx(() -> ((Labeled) required("test-card").lookup(".dialog-title")).getText()))
                .isEqualTo("Advanced features (optional, uses the model)");
        assertThat(shown("test-metric-schema")).isEqualTo("honoured");
        assertThat(shown("test-metric-thinking")).isEqualTo("works with think");
        assertThat(ThemeTestSupport.onFx(() -> required("test-metrics").lookup("#test-metric-samplers")))
                .isNull();
        assertThat(ThemeTestSupport.onFx(() -> required("test-metrics").lookup("#test-metric-cap")))
                .isNull();
    }

    // A generic server's four checks appear as four figures, a missing cap field in words.
    @Test
    void advancedDialog_genericServer_listsFourVerdicts() throws TimeoutException {
        openSettings();
        runAdvanced(ProviderReports.advancedPassed("schema=refused; thinking=ignored; samplers=honoured; cap=none"));

        onFx(() -> ((Hyperlink) required("check-details-ADVANCED")).fire());

        assertThat(shown("test-metric-schema")).isEqualTo("refused");
        assertThat(shown("test-metric-thinking")).isEqualTo("no switch works");
        assertThat(shown("test-metric-samplers")).isEqualTo("honoured");
        assertThat(shown("test-metric-cap")).isEqualTo("no field limits it");
    }

    // The request line is the route the engine was actually reached on, LM Studio's own chat route here.
    @Test
    void requestLine_lmStudioInference_showsTheRouteActuallyUsed() throws TimeoutException {
        openSettings();
        final VerificationReport passed =
                Objects.requireNonNull(ProviderReports.allPassedWithExchanges().data());
        final StageOutcome lmStudio = new StageOutcome(
                VerificationStage.INFERENCE,
                StageStatus.PASSED,
                null,
                null,
                Duration.ofMillis(900),
                null,
                new VerificationExchange(
                        "POST",
                        "http://127.0.0.1:1234/api/v0/chat/completions",
                        "{}",
                        "OK",
                        200,
                        null,
                        null,
                        false,
                        ""));
        checkWithReport(Result.ok(new VerificationReport(
                List.of(passed.stages().get(0), passed.stages().get(1), lmStudio))));

        onFx(() -> ((Hyperlink) required("check-details-INFERENCE")).fire());

        assertThat(shown("test-request-line")).isEqualTo("POST http://127.0.0.1:1234/api/v0/chat/completions");
    }
}
