package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ProgressFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.ViewNames;

/** Start translation on the names and style screen: it begins a run only when Translating would offer a start. */
class NamesStyleScreenTest extends TranslatingScreenTestBase {

    private void showNamesStyle() {
        glossary.willAnswer(Result.ok(List.of()));
        glossary.willAnswer(Result.ok(List.of()));
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
    void screen_shown_holdsHeadingSubtitleNoteAndFooter() throws Exception {
        openBookOnly();
        showNamesStyle();

        assertThat(labelText("names-style-title")).isEqualTo("Names & style");
        assertThat(labelText("names-style-subtitle")).startsWith("The scan lists the names and terms");
        assertThat(labelText("names-style-banner-text"))
                .isEqualTo("Skip this and the app builds names on the fly as it translates.");
        assertThat(button("names-style-back").getText()).isEqualTo("Back to Structure");
        assertThat(button("names-style-start").getText()).isEqualTo("Start translation");
    }

    // IF Back did not lead to Structure, THEN the step could not be left backwards.
    @Test
    void backControl_shown_movesToTheStructureScreen() throws Exception {
        openBookOnly();
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

    private void runningThenBackToNamesStyle() throws Exception {
        readyToStart();
        showTranslating();
        onFx(() -> button("translating-start").fire());
        job.awaitRunStarted();
        awaitBanner("Translating");
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
    }

    // IF a run under way were offered "Start translation" here, THEN the person would think a second one could begin;
    // the button says it goes back to the run, and pressing it asks nothing of the run.
    @Test
    void forward_runUnderWay_offersBackToTheRunAndOnlyShowsIt() throws Exception {
        runningThenBackToNamesStyle();

        assertThat(button("names-style-start").getText()).isEqualTo("Back to the run");
        onFx(() -> button("names-style-start").fire());

        assertThat(engine.requests()).hasSize(1);
        assertThat(job.calls()).doesNotContain("pause", "resume");
        assertThat(currentView()).isEqualTo(ViewNames.TRANSLATING);
    }

    // IF a paused run were started again from here, THEN two jobs would work on one project; the button resumes it.
    @Test
    void forward_runPaused_offersResumeAndResumesTheSameJob() throws Exception {
        readyToStart();
        showTranslating();
        onFx(() -> button("translating-start").fire());
        job.awaitRunStarted();
        awaitBanner("Translating");
        onFx(() -> button("translating-pause").fire());
        job.emit(new Paused(PauseReason.REQUESTED, null, ProgressFixtures.progress(1, 2, 5, 0, 5)));
        awaitBanner("Paused");
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));

        assertThat(button("names-style-start").getText()).isEqualTo("Resume the run");
        onFx(() -> button("names-style-start").fire());

        assertThat(engine.requests()).hasSize(1);
        assertThat(job.calls()).contains("resume");
        assertThat(currentView()).isEqualTo(ViewNames.TRANSLATING);
    }

    // IF a stopped run were offered a fresh start here, THEN Resume would be bypassed by a first-segment run.
    @Test
    void forward_runStopped_offersResumeNotStart() throws Exception {
        readyToStart();
        showTranslating();
        onFx(() -> button("translating-start").fire());
        job.awaitRunStarted();
        awaitBanner("Translating");
        onFx(() -> button("translating-stop").fire());
        job.finish(Result.ok(cancelledReport()));
        awaitBanner("Run stopped");

        showNamesStyleAgain();

        assertThat(button("names-style-start").getText()).isEqualTo("Resume the run");
        assertThat(TooltipProbe.tipText(required("names-style-start")))
                .startsWith("Continues the paused or stopped translation");
    }

    private void showNamesStyleAgain() {
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
    }

    private TableView<?> table() {
        return (TableView<?>) required("names-style-table");
    }

    // IF a column were missing, THEN part of what the person settles could not be seen or edited.
    @Test
    void table_bookOpenEmptyGlossary_hasTheFiveColumnsAndAnEnabledStart() throws Exception {
        openBookOnly();
        showNamesStyle();

        assertThat(ThemeTestSupport.onFx(() -> table().getColumns().stream()
                        .map(column -> ((Label) column.getGraphic()).getText())
                        .toList()))
                .containsExactly("Source term", "Type", "Target", "Gender", "Locked");
        assertThat(ThemeTestSupport.onFx(() -> table().getItems())).isEmpty();
        assertThat(button("names-style-start").isDisabled()).isFalse();
        assertThat(button("names-style-model-scan").getText()).isEqualTo("Model scan");
    }

    // IF a failed glossary read were silent, THEN an empty table would look like a book with no names.
    @Test
    void screen_glossaryReadFails_showsTheServicesMessageInPlace() throws Exception {
        openBookOnly();
        glossary.willAnswer(Result.err(ua.bookloom.api.AppError.of(
                ua.bookloom.api.ErrorCode.internal, "Glossary unavailable", "The glossary could not be read.")));
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));

        assertThat(labelText("names-style-notice-text")).isEqualTo("The glossary could not be read.");
    }

    // IF the table showed with no book, THEN a scan or a start could be pressed on nothing.
    @Test
    void screen_noBookOpen_reportsSoWithARouteToImportAndNoTableScanOrStart() {
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));

        assertThat(labelText("nobook-report")).isNotBlank();
        assertThat(button("nobook-open")).isNotNull();
        assertThat(optional("names-style-table")).isNull();
        assertThat(optional("names-style-model-scan")).isNull();
        assertThat(optional("names-style-start")).isNull();
    }

    // IF a start with no source language reached the engine, THEN the model would be told a language nobody chose.
    @Test
    void startTranslation_noSourceLanguage_isRefusedNamingTheSourceAndAsksForNoJob() throws Exception {
        final java.nio.file.Path notes = java.nio.file.Path.of("notes.txt");
        projects.on(notes, Result.ok(BookFixtures.declaringNoLanguage("notes", BookFormat.TXT, 1)));
        openImport();
        openBook(notes);
        chooseTarget();
        ThemeTestSupport.onFx(() -> {
            injector.getInstance(ua.bookloom.ui.state.SettingsViewModel.class)
                    .model()
                    .set(MODEL);
            return null;
        });
        showNamesStyle();

        onFx(() -> button("names-style-start").fire());

        assertThat(labelText("translating-banner-text"))
                .isEqualTo("Choose the source language on the book brief, then start the translation.");
        assertThat(engine.requests()).isEmpty();
    }
}
