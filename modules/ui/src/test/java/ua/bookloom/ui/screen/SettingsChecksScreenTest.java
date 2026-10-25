package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Button;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.ThemeTestSupport;

/** The three-row grid of checks: one button, one result sentence and one Details link for each stage. */
class SettingsChecksScreenTest extends SettingsScreenTestBase {

    // IF a stage lacked its button, result or link, THEN a person could not run it, read it or open its details.
    @Test
    void grid_opened_hasThreeRowsEachWithButtonResultAndDetailsLink() {
        openSettings();

        assertThat(checksGrid().getId()).isEqualTo("settings-checks-grid");
        assertThat(ThemeTestSupport.onFx(() -> List.of(
                        required("settings-test-connection") instanceof Button,
                        required("settings-test-models") instanceof Button,
                        required("settings-test-inference") instanceof Button)))
                .containsOnly(true);
        assertThat(ThemeTestSupport.onFx(() -> List.of(
                        required("check-details-CONNECTION").isVisible(),
                        required("check-details-MODELS").isVisible(),
                        required("check-details-INFERENCE").isVisible())))
                .hasSize(3);
    }

    // IF the result rows could not take focus, THEN a keyboard user could never open the details of a result.
    @Test
    void resultRows_everyStage_takeKeyboardFocus() {
        openSettings();

        assertThat(ThemeTestSupport.onFx(() -> List.of(
                        result("CONNECTION").isFocusTraversable(),
                        result("MODELS").isFocusTraversable(),
                        result("INFERENCE").isFocusTraversable())))
                .containsOnly(true);
    }

    // IF a failed first stage left the later rows blank, THEN the report would not say what was attempted.
    @Test
    void results_unreachableServer_laterRowsAreNotAttempted() throws TimeoutException {
        openSettings();

        checkWithReport(firstStageOnly(StageStatus.FAILED));

        assertThat(ThemeTestSupport.onFx(() -> result("MODELS").getText())).isEqualTo("– Models: not attempted");
        assertThat(ThemeTestSupport.onFx(() -> result("INFERENCE").getText())).isEqualTo("– Inference: not attempted");
    }

    // IF a long result sentence squeezed the button, THEN "Test connection" would read "Test co..." and hide its
    // action.
    @Test
    void results_longSentence_keepTheTestButtonAtItsPreferredWidth() throws TimeoutException {
        openSettings();
        final AppError missingKey = AppError.of(
                ErrorCode.auth,
                "Key missing",
                "The environment variable BOOKLOOM_SOMETHING_VERY_LONG_API_KEY_NAME is not set or is empty, so no"
                        + " request could be signed and the server was never asked anything at all today.");
        final StageOutcome failed =
                new StageOutcome(VerificationStage.CONNECTION, StageStatus.FAILED, missingKey, null);

        checkWithReport(Result.ok(new VerificationReport(List.of(failed))));

        assertThat(ThemeTestSupport.onFx(
                        () -> button("settings-test-connection").getWidth()))
                .isGreaterThanOrEqualTo(ThemeTestSupport.onFx(
                        () -> button("settings-test-connection").prefWidth(-1)));
    }

    // IF a provider that was only being typed could not be tested, THEN the form would force a Save before a check.
    @Test
    void checks_newProviderTyped_buttonsOnAndTheDraftIsTheSubject() throws TimeoutException {
        openSettings();
        onFx(() -> button("settings-provider-add").fire());
        openFold();
        setField("settings-form-name", "Office LM");
        onFx(() -> form().edit(draft -> draft.withModel(MODEL)));

        assertThat(ThemeTestSupport.onFx(
                        () -> button("settings-test-connection").isDisabled()))
                .isFalse();
        onFx(() -> button("settings-test-connection").fire());
        awaitFx(() -> !viewModel().tests().checking().get());

        assertThat(verifier.drafts()).hasSize(1);
        assertThat(verifier.drafts().get(0).config().id()).isEqualTo("new-provider");
        assertThat(configs.saves()).isZero();
    }

    // IF the buttons stayed on while a test is in flight, THEN a second click would start a second call.
    @Test
    void checks_oneInFlight_allThreeButtonsAreOff() throws Exception {
        verifier.hold();
        openSettings();
        onFx(() -> viewModel().draftModel().set(MODEL));

        onFx(() -> viewModel().tests().run(ua.bookloom.ui.state.ProviderTest.INFERENCE));
        verifier.awaitEntered();

        assertThat(ThemeTestSupport.onFx(() -> List.of(
                        button("settings-test-connection").isDisabled(),
                        button("settings-test-models").isDisabled(),
                        button("settings-test-inference").isDisabled())))
                .containsOnly(true);
        assertThat(ThemeTestSupport.onFx(() -> result("INFERENCE").getText())).isEqualTo("Testing...");
        verifier.release();
    }
}
