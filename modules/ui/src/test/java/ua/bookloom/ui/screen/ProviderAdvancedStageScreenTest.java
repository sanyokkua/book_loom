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

        assertThat(shown("check-result-ADVANCED")).isEqualTo("""
                        ✓ Structured answers: honoured
                        Turning reasoning off: works with reasoning_effort
                        top_k/min_p: ignored
                        Answer cap: max_tokens""");
    }

    // The dialog names the stage and words one row per check; a recognised engine runs two checks, so two rows.
    @Test
    void advancedDialog_recognisedEngine_listsTwoVerdicts() throws TimeoutException {
        openSettings();
        runAdvanced(ProviderReports.advancedPassed("schema=honoured; thinking=think"));

        onFx(() -> ((Hyperlink) required("check-details-ADVANCED")).fire());

        assertThat(ThemeTestSupport.onFx(() -> ((Labeled) required("test-card").lookup(".dialog-title")).getText()))
                .isEqualTo("Advanced features (optional, uses the model)");
        assertThat(textsUnder(required("test-check-schema")))
                .contains("Structured answers", "The answer was exactly the fixed shape.", "honoured");
        assertThat(textsUnder(required("test-check-thinking")))
                .contains("Turning reasoning off", "The think switch worked.", "honoured");
        assertThat(ThemeTestSupport.onFx(() -> required("test-checks").lookup("#test-check-samplers")))
                .isNull();
        assertThat(ThemeTestSupport.onFx(() -> required("test-checks").lookup("#test-check-cap")))
                .isNull();
    }

    // A generic server's four checks appear as four worded rows, a missing cap field as ignored.
    @Test
    void advancedDialog_genericServer_listsFourVerdicts() throws TimeoutException {
        openSettings();
        runAdvanced(ProviderReports.advancedPassed("schema=refused; thinking=ignored; samplers=honoured; cap=none"));

        onFx(() -> ((Hyperlink) required("check-details-ADVANCED")).fire());

        assertThat(textsUnder(required("test-check-schema")))
                .contains("The server answered with an error for this field.", "refused");
        assertThat(textsUnder(required("test-check-thinking")))
                .contains("Reasoning still came back: no switch turned it off.", "ignored");
        assertThat(textsUnder(required("test-check-samplers")))
                .contains("Both answers were the same, so top_k was applied.", "honoured");
        assertThat(textsUnder(required("test-check-cap")))
                .contains("The answer ran past the cap with every field tried.", "ignored");
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
