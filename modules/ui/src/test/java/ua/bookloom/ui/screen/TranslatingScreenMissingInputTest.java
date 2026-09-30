package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.state.RunState;

/** A start refused for a missing input, read from the real scene: the banner names the input and no run begins. */
class TranslatingScreenMissingInputTest extends TranslatingScreenTestBase {

    private void pressStartWithBookOpenAndNoModel() throws TimeoutException {
        projects.on(BOOK, Result.ok(BookFixtures.frankensteinImport()));
        openImport();
        openBook(BOOK);
        chooseTarget();
        showTranslating();
        onFx(() -> button("translating-start").fire());
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void assertNoDialogOrErrorToast() {
        assertThat(optional("error-card")).isNull();
        assertThat(scene.getRoot().lookupAll(".toast-err")).isEmpty();
    }

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
