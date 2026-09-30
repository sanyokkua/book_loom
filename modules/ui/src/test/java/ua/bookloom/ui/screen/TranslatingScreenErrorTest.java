package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.ui.ProgressFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.RunState;

/**
 * The translating dashboard when something goes wrong, read from the real scene: a pause on a provider error as the run's own
 * state with a route to the settings, a refusal in place, the refused start that names its missing input, and no
 * dialog for the pause or the refusal. A failure reaches the screen the way it does in the application, through the mirror.
 */
class TranslatingScreenErrorTest extends TranslatingScreenTestBase {

    private static final String RETRY = "translating-retry-now";
    private static final String SETTINGS = "translating-open-settings";
    private static final String STAY = "translating-stay-paused";
    private static final AppError UNREACHABLE = AppError.of(
            ErrorCode.unreachable,
            "Model server unreachable",
            "Nothing is listening.",
            "endpointHost=localhost:11434",
            null);
    private static final AppError DESTINATION_EXISTS =
            AppError.of(ErrorCode.validation, "Destination exists", "The file Frankenstein.uk.epub already exists.");

    private void failRun(final AppError error) {
        mirror().publishOutcome(RunState.FAILED, null, error);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void pauseOnError(final AppError error) {
        mirror().publishRunState(RunState.PAUSED);
        mirror().review().publishProviderError(error);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void pauseTheJobOnError(final AppError error) throws Exception {
        readyToStart();
        showTranslating();
        onFx(() -> button("translating-start").fire());
        job.awaitRunStarted();
        job.emit(new Paused(PauseReason.ON_ERROR, error, ProgressFixtures.progress(1, 2, 412, 0, 5)));
        awaitFx(() -> isShown(RETRY));
    }

    private List<String> shownActions() {
        return List.of(RETRY, SETTINGS, STAY).stream().filter(this::isShown).toList();
    }

    private Paint barColor() {
        return ThemeTestSupport.onFx(() -> ((Region) progressBar().lookup(".bar"))
                .getBackground()
                .getFills()
                .get(0)
                .getFill());
    }

    private ViewNames currentView() {
        return ThemeTestSupport.onFx(() -> navigator.currentView().get());
    }

    private void assertNoDialogOrErrorToast() {
        assertThat(optional("error-card")).isNull();
        assertThat(scene.getRoot().lookupAll(".toast-err")).isEmpty();
    }

    // --- the provider-error state -----------------------------------------------------------------------------

    // IF the banner did not name the failure and the server, THEN a person could not tell a dead server from a wrong
    // key.
    @Test
    void banner_runPausesOnUnreachable_namesTheTitleTheHostAndThatNoWorkWasLost() {
        showTranslating();
        publish(RunState.RUNNING);

        pauseOnError(UNREACHABLE);

        assertThat(labelText("translating-banner-title")).isEqualTo("Model server unreachable");
        assertThat(labelText("translating-banner-text"))
                .contains("localhost:11434")
                .contains("no work was lost");
        assertThat(required("translating-banner").getStyleClass())
                .contains("banner", "banner-err")
                .doesNotContain("banner-info", "banner-warn");
        assertThat(shownActions()).containsExactly(RETRY, SETTINGS, STAY);
        assertThat(barColor()).isEqualTo(Color.web("#b0574c"));
    }

    // IF a provider code did not reach the same state, THEN one of them would read as an unexplained failure.
    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {
                "unreachable",
                "timeout",
                "auth",
                "rateLimited",
                "upstream",
                "modelNotFound",
                "modelUnavailable",
                "missingCredential"
            })
    void banner_eachProviderCode_showsTheErrorRoleItsTitleAndTheThreeActions(final ErrorCode code) {
        showTranslating();
        publish(RunState.RUNNING);

        pauseOnError(AppError.of(code, "Provider failure " + code.name(), "The provider reported a failure."));

        assertThat(required("translating-banner").getStyleClass()).contains("banner-err");
        assertThat(labelText("translating-banner-title")).isEqualTo("Provider failure " + code.name());
        assertThat(shownActions()).containsExactly(RETRY, SETTINGS, STAY);
        assertNoDialogOrErrorToast();
    }

    // IF LM Studio's unloaded model were shown as a refusal, THEN the person could not retry once it is loaded again.
    @Test
    void banner_pauseOnValidationModelUnloaded_showsTheSameProviderErrorState() {
        showTranslating();
        publish(RunState.RUNNING);

        pauseOnError(AppError.of(ErrorCode.validation, "Model unloaded", "{\"error\":\"Model unloaded\"}"));

        assertThat(required("translating-banner").getStyleClass()).contains("banner-err");
        assertThat(shownActions()).containsExactly(RETRY, SETTINGS, STAY);
    }

    // IF Retry now were not wired, THEN a person who restarted the server could not carry on from this screen.
    @Test
    void retryNow_pressed_resumesTheSameJob() throws Exception {
        pauseTheJobOnError(UNREACHABLE);

        onFx(() -> button(RETRY).fire());

        assertThat(job.calls()).last().isEqualTo("resume");
    }

    // IF Open provider settings resumed the run, THEN the run would fail again before the server was fixed.
    @Test
    void openSettings_pressedInTheProviderErrorState_showsSettingsOnItsProvidersTabAndDoesNotResume() throws Exception {
        pauseTheJobOnError(UNREACHABLE);

        onFx(() -> button(SETTINGS).fire());

        assertThat(currentView()).isEqualTo(ViewNames.SETTINGS);
        assertThat(ThemeTestSupport.onFx(() -> ((TabPane) required("settings-tabs"))
                        .getSelectionModel()
                        .getSelectedItem()
                        .getId()))
                .isEqualTo("settings-tab-providers");
        assertThat(job.calls()).doesNotContain("resume");
    }

    // IF Stay paused kept the three actions, THEN it would offer what it was pressed to decline.
    @Test
    void stayPaused_pressed_withdrawsTheThreeActionsAndLeavesResume() {
        showTranslating();
        publish(RunState.RUNNING);
        pauseOnError(UNREACHABLE);

        onFx(() -> button(STAY).fire());

        assertThat(shownActions()).isEmpty();
        assertThat(isShown("translating-resume")).isTrue();
    }

    // IF a withdrawn state outlived the error, THEN a second failure would be shown with no way to act on it.
    @Test
    void banner_resumeThenASecondPauseAfterStayPaused_showsTheThreeActionsAgain() {
        showTranslating();
        publish(RunState.RUNNING);
        pauseOnError(UNREACHABLE);
        onFx(() -> button(STAY).fire());

        publish(RunState.RUNNING);
        pauseOnError(AppError.of(ErrorCode.timeout, "Model server timed out", "The server did not answer."));

        assertThat(shownActions()).containsExactly(RETRY, SETTINGS, STAY);
        assertThat(labelText("translating-banner-title")).isEqualTo("Model server timed out");
    }

    // IF a refusal offered the settings, THEN the person would be sent to fix a server that was never at fault.
    @Test
    void banner_startRefusedWithValidation_keepsTheNeutralRefusalWithNoSettingsRoute() {
        showTranslating();

        failRun(DESTINATION_EXISTS);

        assertThat(required("translating-banner").getStyleClass()).contains("banner-warn");
        assertThat(shownActions()).isEmpty();
        assertThat(barColor()).isNotEqualTo(Color.web("#b0574c"));
    }

    // IF the bar took the danger role for any pause, THEN an ordinary pause would look like a failure.
    @Test
    void progressBar_pausedWithoutAnError_keepsThePrimaryRole() {
        showTranslating();
        publish(RunState.RUNNING);
        publish(RunState.PAUSED);

        assertThat(progressBar().getStyleClass()).doesNotContain("bar-err");
    }

    // IF the failure wiped the figures, THEN the screen would be the only record of the run, and it would be blank.
    @Test
    void tiles_runFailsAfter412Accepted_stillShow412() {
        showTranslating();
        publish(RunState.RUNNING);
        publishProgress(412, 3, 825);

        pauseOnError(UNREACHABLE);

        assertThat(labelText("translating-count-accepted")).isEqualTo("412");
        assertThat(labelText("translating-count-flagged")).isEqualTo("3");
        assertThat(labelText("translating-count-remaining")).isEqualTo("825");
    }

    // IF the provider-error state also opened a dialog, THEN the same failure would be said twice.
    @Test
    void dialog_providerFailure_opensNoDialogAndRaisesNoErrorToast() {
        showTranslating();
        publish(RunState.RUNNING);

        pauseOnError(UNREACHABLE);

        assertNoDialogOrErrorToast();
    }

    // IF a new run kept the old provider error on screen, THEN a healthy run would still read as failed.
    @Test
    void banner_newRunBeginsAfterAProviderError_returnsToThePlainRunningBanner() {
        showTranslating();
        publish(RunState.RUNNING);
        pauseOnError(UNREACHABLE);

        publish(RunState.RUNNING);

        assertThat(labelText("translating-banner-title")).isEqualTo("Translating");
        assertThat(required("translating-banner").getStyleClass())
                .contains("banner-info")
                .doesNotContain("banner-err");
        assertThat(isShown("translating-open-settings")).isFalse();
    }

    // IF a run that ended failed with a provider code still drew the provider-error banner, THEN a run that cannot be
    // resumed would offer a Retry that has nothing to retry.
    @Test
    void dialog_runFailedWithAProviderCode_opensTheDialogAndShowsNoProviderBanner() {
        showTranslating();
        publish(RunState.RUNNING);

        failRun(UNREACHABLE);

        assertThat(optional("error-card")).isNotNull();
        assertThat(required("translating-banner").getStyleClass()).doesNotContain("banner-err");
        assertThat(isShown("translating-open-settings")).isFalse();
    }

    // --- a refusal in place -----------------------------------------------------------------------------------

    // IF a destination that already exists looked like a dead server, THEN the person would fix a machine that was
    // never broken.
    @Test
    void banner_runRefusedWithValidation_isAWarningWithTheMessageAndNoSettingsRoute() {
        showTranslating();

        failRun(DESTINATION_EXISTS);

        assertThat(labelText("translating-banner-text")).contains("The file Frankenstein.uk.epub already exists.");
        assertThat(required("translating-banner").getStyleClass())
                .contains("banner", "banner-warn")
                .doesNotContain("banner-err");
        assertThat(labelText("translating-banner-title")).doesNotContainIgnoringCase("unreachable");
        assertThat(isShown("translating-open-settings")).isFalse();
        assertNoDialogOrErrorToast();
    }

    // IF the refusal title claimed the run was never started, THEN an export refused after a whole book was translated
    // would tell the person that nothing had been done.
    @Test
    void banner_validationAfterProgress_isTitledNeutrallyAndKeepsTheCounts() {
        showTranslating();
        publish(RunState.RUNNING);
        publishProgress(412, 3, 825);

        failRun(DESTINATION_EXISTS);

        assertThat(labelText("translating-banner-title")).isEqualTo("The run was refused");
        assertThat(required("translating-banner").getStyleClass()).contains("banner-warn");
        assertThat(labelText("translating-count-accepted")).isEqualTo("412");
        assertThat(labelText("translating-count-flagged")).isEqualTo("3");
        assertThat(labelText("translating-count-remaining")).isEqualTo("825");
    }

    // IF a code that is not a provider failure offered the provider settings, THEN the person would be sent to the
    // wrong screen to fix it.
    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {"validation", "discoveryFailed"})
    void openSettings_codeThatIsNotAProviderFailure_isNotOffered(final ErrorCode code) {
        showTranslating();
        publish(RunState.RUNNING);

        failRun(AppError.of(code, "Not a provider failure", "Something else happened."));

        assertThat(isShown("translating-open-settings")).isFalse();
        assertThat(required("translating-banner").getStyleClass()).doesNotContain("banner-err");
    }

    // IF a cancelled result were drawn as a failed run, THEN a stop would read as "did not finish" instead of
    // "stopped".
    @Test
    void banner_runReturnsCancelledAsAnError_showsTheStoppedStateAndNoErrorRole() {
        showTranslating();
        publish(RunState.RUNNING);

        mirror().publishOutcome(RunState.STOPPED, null, null);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(labelText("translating-banner-title")).isEqualTo("Run stopped");
        assertThat(required("translating-banner").getStyleClass())
                .contains("banner-info")
                .doesNotContain("banner-err", "banner-warn");
        assertThat(isShown("translating-open-settings")).isFalse();
        assertNoDialogOrErrorToast();
    }

    // IF a stop drew the provider-error state or a dialog, THEN a choice the person made would read as a failure.
    @Test
    void banner_runStoppedByThePerson_showsNeitherTheSettingsRouteNorADialog() {
        showTranslating();
        publish(RunState.RUNNING);

        mirror().publishOutcome(RunState.STOPPED, cancelledReport(), null);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(labelText("translating-banner-title")).isEqualTo("Run stopped");
        assertThat(isShown("translating-open-settings")).isFalse();
        assertNoDialogOrErrorToast();
    }
}
