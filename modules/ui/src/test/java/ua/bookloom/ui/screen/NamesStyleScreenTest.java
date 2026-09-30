package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.ui.ProgressFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;

/** Start translation on the names and style screen: it begins a run only when Translating would offer a start. */
class NamesStyleScreenTest extends TranslatingScreenTestBase {

    private void showNamesStyle() {
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
    }

    private ViewNames currentView() {
        return ThemeTestSupport.onFx(() -> navigator.currentView().get());
    }

    private void awaitBanner(final String title) throws Exception {
        awaitFx(() -> title.equals(labelText("translating-banner-title")));
    }

    private void openBookOnly() throws TimeoutException {
        projects.on(BOOK, Result.ok(ua.bookloom.ui.BookFixtures.frankensteinImport()));
        openImport();
        openBook(BOOK);
        chooseTarget();
    }

    // IF the screen had no heading, subtitle, note or footer, THEN the step would be an empty wall.
    @Test
    void screen_shown_holdsHeadingSubtitleNoteAndFooter() {
        showNamesStyle();

        assertThat(labelText("names-style-title")).isEqualTo("Names & style");
        assertThat(labelText("names-style-subtitle")).startsWith("A quick scan proposes");
        assertThat(labelText("names-style-banner-text"))
                .isEqualTo("Skip this and the app builds names on the fly as it translates.");
        assertThat(button("names-style-back").getText()).isEqualTo("Back to Structure");
        assertThat(button("names-style-start").getText()).isEqualTo("Start translation");
    }

    // IF Back did not lead to Structure, THEN the step could not be left backwards.
    @Test
    void backControl_shown_movesToTheStructureScreen() {
        showNamesStyle();

        onFx(() -> button("names-style-back").fire());

        assertThat(currentView()).isEqualTo(ViewNames.STRUCTURE);
    }

    // IF a start with no model still reached the engine, THEN a run would begin on nothing.
    @Test
    void startTranslation_noModel_showsTranslatingNamingTheModelAndAsksForNoJob() throws Exception {
        openBookOnly();
        showNamesStyle();

        onFx(() -> button("names-style-start").fire());

        assertThat(currentView()).isEqualTo(ViewNames.TRANSLATING);
        assertThat(labelText("translating-banner-title")).isEqualTo("Cannot start yet");
        assertThat(labelText("translating-banner-text"))
                .isEqualTo("Choose a model in the provider settings, then start the translation.");
        assertThat(engine.requests()).isEmpty();
    }

    // IF the button did not start the run, THEN the reference's start point would do nothing.
    @Test
    void startTranslation_bookAndModelReady_asksForOneJobAndShowsTranslating() throws Exception {
        readyToStart();
        showNamesStyle();

        onFx(() -> button("names-style-start").fire());

        job.awaitRunStarted();
        assertThat(engine.requests()).hasSize(1);
        assertThat(currentView()).isEqualTo(ViewNames.TRANSLATING);
    }

    // IF a paused run were started again from here, THEN two jobs would work on one project.
    @Test
    void startTranslation_runPaused_asksForNoNewJobAndTranslatingOffersResume() throws Exception {
        readyToStart();
        showTranslating();
        onFx(() -> button("translating-start").fire());
        job.awaitRunStarted();
        awaitBanner("Translating");
        onFx(() -> button("translating-pause").fire());
        job.emit(new Paused(PauseReason.REQUESTED, null, ProgressFixtures.progress(1, 2, 5, 0, 5)));
        awaitBanner("Paused");
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));

        onFx(() -> button("names-style-start").fire());

        assertThat(engine.requests()).hasSize(1);
        assertThat(currentView()).isEqualTo(ViewNames.TRANSLATING);
        assertThat(enabledControls()).contains("translating-resume");
        assertThat(button("translating-resume").isDisabled()).isFalse();
    }

    // IF a stopped run were started again from here, THEN Resume would be bypassed by a fresh first-segment run.
    @Test
    void startTranslation_runStopped_asksForNoNewJobAndTranslatingOffersResume() throws Exception {
        readyToStart();
        showTranslating();
        onFx(() -> button("translating-start").fire());
        job.awaitRunStarted();
        awaitBanner("Translating");
        onFx(() -> button("translating-stop").fire());
        job.finish(Result.ok(cancelledReport()));
        awaitBanner("Run stopped");
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));

        onFx(() -> button("names-style-start").fire());

        assertThat(engine.requests()).hasSize(1);
        assertThat(currentView()).isEqualTo(ViewNames.TRANSLATING);
        assertThat(enabledControls()).containsExactly("translating-resume");
        showNamesStyleAgain();
        assertThat(button("names-style-start").getText()).isEqualTo("Start translation");
    }

    private void showNamesStyleAgain() {
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
    }
}
