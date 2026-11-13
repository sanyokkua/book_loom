package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.ActivityTracker;

/** A run that translates holds the model tests that would send a request; a paused run lets them through. */
class ProviderChecksBusyTest extends SettingsScreenTestBase {

    private static final String SENTENCE =
            "BookLoom is translating. Try again when the run ends or while it is paused.";
    private static final String GEMMA = "gemma4:e4b";

    private ActivityTracker.Handle beginRun() {
        return ThemeTestSupport.onFx(
                () -> injector.getInstance(ActivityTracker.class).begin(ActivityKind.TRANSLATION, () -> {}));
    }

    private void open() {
        openSettings();
        onFx(() -> viewModel().draftModel().set(GEMMA));
    }

    // IF the test were left on while a run translates, THEN it would queue a second request on the single model.
    @Test
    void inferenceTest_runTranslating_showsSentenceAndSendsNothing() {
        open();

        beginRun();

        assertThat(button("settings-test-inference").isDisable()).isTrue();
        assertThat(text("settings-check-refusal")).isEqualTo(SENTENCE);
        assertThat(verifier.callCount()).isZero();
    }

    // IF a paused run still refused the test, THEN the sentence that says "while it is paused" would be false.
    @Test
    void inferenceTest_runPaused_runs() throws Exception {
        open();
        final ActivityTracker.Handle run = beginRun();
        onFx(run::end);

        onFx(() -> button("settings-test-inference").fire());
        verifier.awaitEntered();

        assertThat(verifier.callCount()).isEqualTo(1);
        assertThat(ThemeTestSupport.onFx(
                        () -> scene.getRoot().lookup("#settings-check-refusal").isVisible()))
                .isFalse();
    }

    // IF Test connection were refused too, THEN a person could not check an address while a book translates.
    @Test
    void connectionTest_runTranslating_runsAndIsNotRefused() throws Exception {
        open();
        beginRun();
        assertThat(button("settings-test-connection").isDisable()).isFalse();

        onFx(() -> button("settings-test-connection").fire());
        verifier.awaitEntered();

        assertThat(verifier.callCount()).isEqualTo(1);
    }
}
