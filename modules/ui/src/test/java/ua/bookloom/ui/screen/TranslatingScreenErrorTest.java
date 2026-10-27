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
 * The Translating screen when something goes wrong, read from the real scene: a pause on an error as the run's own
 * paused-after-an-error row with Try again now, Skip and Open settings, a missing model with another model to choose, a
 * refusal in place, a failed run's banner with Show details, and no dialog that opens by itself. A failure reaches the
 * screen the way it does in the application, through the mirror.
 */
class TranslatingScreenErrorTest extends TranslatingScreenTestBase {

    private static final String RETRY = "translating-retry-now";
    private static final String SKIP = "translating-skip-segment";
    private static final String SETTINGS = "translating-open-settings";
    private static final String CHOOSE_MODEL = "translating-choose-model";
    private static final String SHOW_DETAILS = "translating-show-details";
    private static final AppError UNREACHABLE = AppError.of(
            ErrorCode.unreachable,
            "Model server unreachable",
            "Nothing is listening.",
            "endpointHost=localhost:11434",
            null);
    private static final AppError DESTINATION_EXISTS =
            AppError.of(ErrorCode.validation, "Destination exists", "The file Frankenstein.uk.epub already exists.");

    /** A run on the shell: the model chosen and a run file published, so the run's banner is pinned. */
    private void onTheShell() {
        chooseModel();
        showTranslating();
        mirror().publishRunStarted("Frankenstein.epub", null);
        WaitForAsyncUtils.waitForFxEvents();
    }

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
        awaitFx(() -> optional("translating-banner") != null);
    }

    private List<String> shownActions() {
        return List.of(RETRY, SKIP, SETTINGS, CHOOSE_MODEL, SHOW_DETAILS).stream()
                .filter(this::isShown)
                .toList();
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

    // --- a pause on an error ----------------------------------------------------------------------------------

    // IF the banner did not name the program and what to do, THEN a person could not tell a closed program from a
    // wrong key; it offers the three things a person can do.
    @Test
    void banner_runPausesOnUnreachable_namesTheProgramWithTheThreeActions() {
        onTheShell();
        publish(RunState.RUNNING);

        pauseOnError(UNREACHABLE);

        assertThat(labelText("translating-banner-title")).isEqualTo("Paused. Ollama could not be reached.");
        assertThat(labelText("translating-banner-text")).isEqualTo("Open Ollama, then press Resume.");
        assertThat(required("translating-banner").getStyleClass())
                .contains("banner", "banner-err")
                .doesNotContain("banner-info", "banner-warn");
        assertThat(shownActions()).containsExactly(RETRY, SKIP, SETTINGS);
        assertThat(barColor()).isEqualTo(Color.web("#b0574c"));
    }

    // IF a provider code did not reach the same state, THEN one of them would read as an unexplained failure.
    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {"unreachable", "timeout", "auth", "rateLimited", "upstream", "missingCredential", "validation"})
    void banner_eachErrorARunPausesOn_showsTheErrorRoleAndTheThreeActions(final ErrorCode code) {
        onTheShell();
        publish(RunState.RUNNING);

        pauseOnError(AppError.of(code, "Provider failure " + code.name(), "The provider reported a failure."));

        assertThat(required("translating-banner").getStyleClass()).contains("banner-err");
        assertThat(labelText("translating-banner-title")).startsWith("Paused. Ollama");
        assertThat(shownActions()).containsExactly(RETRY, SKIP, SETTINGS);
        assertNoDialogOrErrorToast();
    }

    // IF an unloaded model were told as an outage, THEN the person would restart a program that answers.
    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {"modelNotFound", "modelUnavailable"})
    void banner_modelMissing_namesTheModelAndOffersAnotherModel(final ErrorCode code) {
        onTheShell();
        publish(RunState.RUNNING);

        pauseOnError(AppError.of(code, "Model unloaded", "{\"error\":\"Model unloaded\"}"));

        assertThat(labelText("translating-banner-title"))
                .isEqualTo("The AI model gemma3:12b is not available — check that it is installed.");
        assertThat(required("translating-banner").getStyleClass()).contains("banner-err");
        assertThat(shownActions()).containsExactly(CHOOSE_MODEL);
        assertThat(button("translating-resume").getText()).isEqualTo("Try again now");
        assertNoDialogOrErrorToast();
    }

    // IF Try again now were not wired, THEN a person who opened the program again could not carry on from this screen.
    @Test
    void retryNow_pressed_resumesTheSameJob() throws Exception {
        pauseTheJobOnError(UNREACHABLE);

        onFx(() -> button(RETRY).fire());

        assertThat(job.calls()).last().isEqualTo("resume");
    }

    // IF Open settings resumed the run, THEN the run would fail again before the program was fixed.
    @Test
    void openSettings_pressedOnTheBanner_showsSettingsOnItsProvidersTabAndDoesNotResume() throws Exception {
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

    // IF the banner vanished off Translating, THEN a person in Settings fixing the program would lose the way back.
    @Test
    void banner_onSettings_staysPinnedAndOffersGoToTranslating() throws Exception {
        pauseTheJobOnError(UNREACHABLE);

        onFx(() -> button(SETTINGS).fire());

        assertThat(required("translating-banner").getParent()).isSameAs(required("shell-banners"));
        assertThat(isShown("translating-go-translating")).isTrue();
        onFx(() -> button("translating-go-translating").fire());
        assertThat(currentView()).isEqualTo(ViewNames.TRANSLATING);
        assertThat(isShown("translating-go-translating")).isFalse();
    }

    // IF Choose another model resumed the run, THEN the missing model would fail it again at once.
    @Test
    void chooseAnotherModel_pressed_showsSettingsAndDoesNotResume() throws Exception {
        pauseTheJobOnError(AppError.of(ErrorCode.modelUnavailable, "Model unloaded", "Model unloaded."));

        onFx(() -> button(CHOOSE_MODEL).fire());

        assertThat(currentView()).isEqualTo(ViewNames.SETTINGS);
        assertThat(job.calls()).doesNotContain("resume");
    }

    // IF a refusal offered the settings, THEN the person would be sent to fix a program that was never at fault.
    @Test
    void banner_startRefusedWithValidation_keepsTheNeutralRefusalWithNoSettingsRoute() {
        onTheShell();

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

        assertThat(labelText("translating-count-translated")).isEqualTo("412 translated");
        assertThat(labelText("translating-count-to-review")).isEqualTo("3 to review");
        assertThat(labelText("translating-strip-line")).startsWith("415 of 1,240 segments done");
    }

    // IF the paused state also opened a dialog, THEN the same failure would be said twice.
    @Test
    void dialog_providerFailure_opensNoDialogAndRaisesNoErrorToast() {
        onTheShell();
        publish(RunState.RUNNING);

        pauseOnError(UNREACHABLE);

        assertNoDialogOrErrorToast();
    }

    // IF a new run kept the old error on screen, THEN a healthy run would still read as failed.
    @Test
    void banner_runResumesAfterAnError_isWithdrawn() {
        onTheShell();
        publish(RunState.RUNNING);
        pauseOnError(UNREACHABLE);

        publish(RunState.RUNNING);

        assertThat(optional("translating-banner")).isNull();
        assertThat(isShown("translating-open-settings")).isFalse();
    }

    // IF a failed run opened a dialog by itself or offered a Retry, THEN it would interrupt the person or offer what
    // cannot be done; its banner says what happened and opens the failure on Show details.
    @Test
    void banner_runFailedWithAProviderCode_showsTheFailedBannerAndNoDialogUntilShowDetails() {
        onTheShell();
        publish(RunState.RUNNING);

        failRun(UNREACHABLE);
        final boolean dialogBefore = optional("error-card") != null;
        onFx(() -> button(SHOW_DETAILS).fire());

        assertThat(dialogBefore).isFalse();
        assertThat(labelText("translating-banner-title"))
                .isEqualTo("The translation stopped because of an error. Finished segments are saved.");
        assertThat(required("translating-banner").getStyleClass()).contains("banner-err");
        assertThat(isShown(SETTINGS)).isFalse();
        assertThat(optional("error-card")).isNotNull();
    }

    // --- a refusal in place -----------------------------------------------------------------------------------

    // IF a destination that already exists looked like a dead program, THEN the person would fix a machine that was
    // never broken.
    @Test
    void banner_runRefusedWithValidation_isAWarningWithTheMessageAndNoSettingsRoute() {
        onTheShell();

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
        onTheShell();
        publish(RunState.RUNNING);
        publishProgress(412, 3, 825);

        failRun(DESTINATION_EXISTS);

        assertThat(labelText("translating-banner-title")).isEqualTo("The run was refused");
        assertThat(required("translating-banner").getStyleClass()).contains("banner-warn");
        assertThat(labelText("translating-count-translated")).isEqualTo("412 translated");
        assertThat(labelText("translating-count-to-review")).isEqualTo("3 to review");
        assertThat(labelText("translating-strip-line")).startsWith("415 of 1,240 segments done");
    }

    // IF a code that is not a provider failure offered the provider settings, THEN the person would be sent to the
    // wrong screen to fix it.
    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {"validation", "discoveryFailed"})
    void openSettings_codeThatIsNotAProviderFailure_isNotOffered(final ErrorCode code) {
        onTheShell();
        publish(RunState.RUNNING);

        failRun(AppError.of(code, "Not a provider failure", "Something else happened."));

        assertThat(isShown("translating-open-settings")).isFalse();
        assertNoDialogOrErrorToast();
    }

    // IF a cancelled result were drawn as a failed run, THEN a stop would read as "did not finish" instead of
    // "stopped".
    @Test
    void stopped_runReturnsCancelledAsAnError_showsTheStoppedStateAndNoBanner() {
        onTheShell();
        publish(RunState.RUNNING);

        mirror().publishOutcome(RunState.STOPPED, null, null);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(labelText("shell-run-state")).startsWith("Stopped at");
        assertThat(optional("translating-banner")).isNull();
        assertThat(isShown("translating-open-settings")).isFalse();
        assertNoDialogOrErrorToast();
    }

    // IF a stop drew the provider-error state or a dialog, THEN a choice the person made would read as a failure.
    @Test
    void stopped_byThePerson_showsNeitherTheSettingsRouteNorADialog() {
        onTheShell();
        publish(RunState.RUNNING);

        mirror().publishOutcome(RunState.STOPPED, cancelledReport(), null);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(labelText("shell-run-state")).startsWith("Stopped at");
        assertThat(isShown("translating-open-settings")).isFalse();
        assertNoDialogOrErrorToast();
    }
}
