package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.RunState;

/**
 * The translating dashboard when something goes wrong, read from the real scene: a provider failure as the run's own
 * state with a route to the settings, a refusal in place, the refused start that names its missing input, and no
 * dialog opened for any of them. A failure reaches the screen the way it does in the application, through the mirror.
 */
class TranslatingScreenErrorTest extends TranslatingScreenTestBase {

    private static final AppError UNREACHABLE =
            AppError.of(ErrorCode.unreachable, "Unreachable", "Nothing is listening at http://localhost:11434.");
    private static final AppError DESTINATION_EXISTS =
            AppError.of(ErrorCode.validation, "Destination exists", "The file Frankenstein.uk.epub already exists.");

    private void failRun(final AppError error) {
        mirror().publishOutcome(RunState.FAILED, null, error);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private ViewNames currentView() {
        return ThemeTestSupport.onFx(() -> navigator.currentView().get());
    }

    private void assertNoDialogOrErrorToast() {
        assertThat(optional("error-card")).isNull();
        assertThat(scene.getRoot().lookupAll(".toast-err")).isEmpty();
    }

    private void pressStartWithBookOpenAndNoModel() throws TimeoutException {
        port.on(BOOK, Result.ok(BookFixtures.frankenstein()));
        openImport();
        openBook(BOOK);
        showTranslating();
        onFx(() -> button("translating-start").fire());
        WaitForAsyncUtils.waitForFxEvents();
    }

    // --- the provider-error state -----------------------------------------------------------------------------

    // IF the banner did not name the code, THEN a person could not tell a dead server from a wrong key.
    @Test
    void banner_runFailsWithUnreachable_namesTheCodeAndCarriesTheMessageInTheErrorRole() {
        showTranslating();
        publish(RunState.RUNNING);

        failRun(UNREACHABLE);

        assertThat(labelText("translating-banner-title")).containsIgnoringCase("unreachable");
        assertThat(labelText("translating-banner-text")).contains("Nothing is listening at http://localhost:11434.");
        assertThat(required("translating-banner").getStyleClass())
                .contains("banner", "banner-err")
                .doesNotContain("banner-info", "banner-warn");
    }

    // IF every provider code did not reach the same state, THEN one of the ten would read as an unexplained failure.
    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {
                "unreachable",
                "timeout",
                "auth",
                "rateLimited",
                "upstream",
                "emptyCompletion",
                "modelNotFound",
                "modelUnavailable",
                "missingCredential",
                "contextWindow"
            })
    void banner_eachProviderCode_showsTheErrorRoleItsCodeNameAndTheSettingsRoute(final ErrorCode code) {
        showTranslating();
        publish(RunState.RUNNING);

        failRun(AppError.of(code, "Provider failure", "The provider reported " + code.name() + "."));

        assertThat(required("translating-banner").getStyleClass()).contains("banner-err");
        assertThat(labelText("translating-banner-title")).containsIgnoringCase(code.name());
        assertThat(isShown("translating-open-settings")).isTrue();
        assertNoDialogOrErrorToast();
    }

    // IF the provider-error state offered no way out, THEN the person would be left looking at a message about a
    // server they have to go and find the setting for.
    @Test
    void openSettings_providerErrorShown_offersTheRouteToTheProviderSettings() {
        showTranslating();
        publish(RunState.RUNNING);

        failRun(UNREACHABLE);

        assertThat(isShown("translating-open-settings")).isTrue();
        assertThat(button("translating-open-settings").getText()).isEqualTo("Open provider settings");
    }

    // IF the offered route did nothing, THEN the state would promise a fix it cannot deliver.
    @Test
    void openSettings_pressed_makesTheSettingsScreenTheCurrentView() {
        showTranslating();
        publish(RunState.RUNNING);
        failRun(UNREACHABLE);
        assertThat(currentView()).isEqualTo(ViewNames.TRANSLATING);

        onFx(() -> button("translating-open-settings").fire());

        assertThat(currentView()).isEqualTo(ViewNames.SETTINGS);
    }

    // IF the failure wiped the figures, THEN the screen would be the only record of the run, and it would be blank.
    @Test
    void tiles_runFailsAfter412Accepted_stillShow412() {
        showTranslating();
        publish(RunState.RUNNING);
        publishProgress(412, 3, 825);

        failRun(UNREACHABLE);

        assertThat(labelText("translating-count-accepted")).isEqualTo("412");
        assertThat(labelText("translating-count-flagged")).isEqualTo("3");
        assertThat(labelText("translating-count-remaining")).isEqualTo("825");
    }

    // IF the provider-error state also opened a dialog, THEN the same failure would be said twice.
    @Test
    void dialog_providerFailure_opensNoDialogAndRaisesNoErrorToast() {
        showTranslating();
        publish(RunState.RUNNING);

        failRun(UNREACHABLE);

        assertNoDialogOrErrorToast();
    }

    // IF a new run kept the old provider error on screen, THEN a healthy run would still read as failed.
    @Test
    void banner_newRunBeginsAfterAProviderError_returnsToThePlainRunningBanner() {
        showTranslating();
        publish(RunState.RUNNING);
        failRun(UNREACHABLE);

        publish(RunState.RUNNING);

        assertThat(labelText("translating-banner-title")).isEqualTo("Translating");
        assertThat(required("translating-banner").getStyleClass())
                .contains("banner-info")
                .doesNotContain("banner-err");
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

    // --- a start with an input missing ------------------------------------------------------------------------

    // IF Start with no model did nothing visible, THEN a person would press it and get no answer.
    @Test
    void banner_startWithNoModelChosen_namesTheModelAsAWarningAndStartsNoRun() throws TimeoutException {
        pressStartWithBookOpenAndNoModel();

        assertThat(labelText("translating-banner-text")).containsIgnoringCase("model");
        assertThat(required("translating-banner").getStyleClass())
                .contains("banner", "banner-warn")
                .doesNotContain("banner-err");
        assertThat(isShown("translating-open-settings")).isFalse();
        assertThat(models.selections()).isEmpty();
        assertThat(engine.requests()).isEmpty();
        assertNoDialogOrErrorToast();
    }

    // IF Start with no book named the model, THEN the person would be sent to fix the wrong step.
    @Test
    void banner_startWithNoBookOpen_namesTheBookAsAWarningAndStartsNoRun() {
        showTranslating();

        onFx(() -> button("translating-start").fire());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(labelText("translating-banner-text")).containsIgnoringCase("book");
        assertThat(labelText("translating-banner-text")).doesNotContainIgnoringCase("model");
        assertThat(required("translating-banner").getStyleClass()).contains("banner-warn");
        assertThat(models.selections()).isEmpty();
        assertThat(engine.requests()).isEmpty();
        assertNoDialogOrErrorToast();
    }

    // IF a refusal stayed once a run began, THEN a run in progress would still say something was missing.
    @Test
    void banner_runBeginsAfterARefusedStart_returnsToThePlainRunningBanner() throws TimeoutException {
        pressStartWithBookOpenAndNoModel();

        publish(RunState.RUNNING);

        assertThat(labelText("translating-banner-title")).isEqualTo("Translating");
        assertThat(required("translating-banner").getStyleClass())
                .contains("banner-info")
                .doesNotContain("banner-warn");
    }
}
