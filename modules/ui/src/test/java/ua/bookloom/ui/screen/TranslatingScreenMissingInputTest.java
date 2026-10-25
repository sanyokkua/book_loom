package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
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

    // IF Start with no target language began a run, THEN the model would be asked to translate into nothing; the
    // banner names the missing language and Start is off.
    @Test
    void cannotStart_noTargetLanguage_namesItAndKeepsStartOff() throws TimeoutException {
        projects.on(BOOK, Result.ok(BookFixtures.frankensteinImport()));
        openImport();
        openBook(BOOK);
        chooseModel();
        showTranslating();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(labelText("translating-banner-title")).isEqualTo("Cannot start yet — no target language is chosen.");
        assertThat(button("translating-start").isDisabled()).isTrue();
        assertThat(engine.requests()).isEmpty();
        assertNoDialogOrErrorToast();
    }

    // IF the banner of a missing target language led to Translating, THEN its button would lead away from where the
    // language is chosen (and do nothing while the step is locked); it leads to the Book Brief and is gone there.
    @Test
    void cannotStart_noTargetLanguage_goToBookBriefOpensTheBriefWhereTheBannerIsGone() throws TimeoutException {
        projects.on(BOOK, Result.ok(BookFixtures.frankensteinImport()));
        openImport();
        openBook(BOOK);
        chooseModel();
        onFx(() -> shell.activate(ViewNames.IMPORT));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(button("translating-go-brief").getText()).isEqualTo("Go to Book Brief");
        assertThat(optional("translating-go-translating")).isNull();
        onFx(() -> button("translating-go-brief").fire());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(injector.getInstance(Navigator.class).currentView().get()).isEqualTo(ViewNames.BOOK_BRIEF);
        assertThat(optional("translating-banner")).isNull();
    }
}
