package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.state.RunState;

/**
 * A start that lacks an input, read from the real scene (README "Run states": Cannot start yet): with no AI model chosen
 * Start is off with a hover that names the model, the banner says so with Open settings, and no run begins.
 */
class TranslatingScreenMissingInputTest extends TranslatingScreenTestBase {

    private void bookOpenAndNoModel() throws TimeoutException {
        projects.on(BOOK, Result.ok(BookFixtures.frankensteinImport()));
        openImport();
        openBook(BOOK);
        chooseTarget();
        showTranslating();
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void assertNoDialogOrErrorToast() {
        assertThat(optional("error-card")).isNull();
        assertThat(scene.getRoot().lookupAll(".toast-err")).isEmpty();
    }

    // IF Start with no model could be pressed for nothing, THEN a person would get no answer; it is off, says why, and
    // the banner leads to the settings.
    @Test
    void cannotStart_noModelChosen_offersOpenSettingsAndKeepsStartOffWithTheReason() throws TimeoutException {
        bookOpenAndNoModel();

        assertThat(labelText("translating-banner-title")).isEqualTo("Cannot start yet — no AI model is chosen.");
        assertThat(labelText("translating-banner-text"))
                .isEqualTo("Choose an AI model in Settings › Providers, then start the translation.");
        assertThat(required("translating-banner").getStyleClass())
                .contains("banner", "banner-warn")
                .doesNotContain("banner-err");
        assertThat(isShown("translating-open-settings")).isTrue();
        assertThat(button("translating-start").isDisabled()).isTrue();
        assertThat(Tips.explanationOf(button("translating-start")))
                .contains("Choose an AI model in Settings › Providers, then start the translation.");
        assertThat(button("shell-run-connection").getText()).isEqualTo("No AI model");
        assertThat(models.selections()).isEmpty();
        assertThat(engine.requests()).isEmpty();
        assertNoDialogOrErrorToast();
    }

    // IF choosing a model left the refusal standing, THEN a ready start would still read as refused.
    @Test
    void cannotStart_modelThenChosen_isReadyWithStartOn() throws TimeoutException {
        bookOpenAndNoModel();

        chooseModel();

        assertThat(optional("translating-banner")).isNull();
        assertThat(button("translating-start").isDisabled()).isFalse();
        assertThat(labelText("shell-run-state")).isEqualTo("Ready");
    }

    // IF Start with no book began a run, THEN a run would exist with nothing to translate.
    @Test
    void start_noBookOpen_startsNoRunAndOpensNoDialog() {
        chooseModel();
        showTranslating();

        onFx(() -> button("translating-start").fire());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(models.selections()).isEmpty();
        assertThat(engine.requests()).isEmpty();
        assertNoDialogOrErrorToast();
    }

    // IF a refusal stayed once a run began, THEN a run in progress would still say something was missing.
    @Test
    void banner_runBegins_withdrawsTheCannotStartBanner() throws TimeoutException {
        bookOpenAndNoModel();

        publish(RunState.RUNNING);

        assertThat(optional("translating-banner")).isNull();
    }
}
