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

    // The title bar's state text of the run-state row, which every surface follows.
    private void awaitState(final String prefix) throws Exception {
        awaitFx(() -> labelText("shell-run-state").startsWith(prefix));
    }

    private void openBookOnly() throws TimeoutException {
        projects.on(BOOK, Result.ok(ua.bookloom.ui.BookFixtures.frankensteinImport()));
        openImport();
        openBook(BOOK);
        chooseTarget();
    }

    // IF the screen had no heading, purpose line or footer, THEN the step would be an empty wall.
    @Test
    void screen_shown_holdsHeadingPurposeAndFooter() throws Exception {
        openBookOnly();
        showNamesStyle();

        assertThat(labelText("names-style-title")).isEqualTo("Names & style");
        assertThat(labelText("names-style-purpose"))
                .isEqualTo("Decide how names and repeated words are translated. Optional — skip it and BookLoom"
                        + " builds both while it translates.");
        assertThat(button("names-style-back").getText()).isEqualTo("Back to Book Brief");
        assertThat(button("names-style-start").getText()).isEqualTo("Start translation");
    }

    // IF Back did not lead to the Book Brief, THEN the step could not be left backwards.
    @Test
    void backControl_shown_movesToTheBookBriefScreen() throws Exception {
        openBookOnly();
        showNamesStyle();

        onFx(() -> button("names-style-back").fire());

        assertThat(currentView()).isEqualTo(ViewNames.BOOK_BRIEF);
    }

    // IF a start with no model still reached the engine, THEN a run would begin on nothing.
    @Test
    void startTranslation_noModel_showsTranslatingNamingTheModelAndAsksForNoJob() throws Exception {
        openBookOnly();
        showNamesStyle();

        onFx(() -> button("names-style-start").fire());

        assertThat(currentView()).isEqualTo(ViewNames.TRANSLATING);
        assertThat(labelText("translating-banner-title")).isEqualTo("Cannot start yet — no AI model is chosen.");
        assertThat(labelText("translating-banner-text"))
                .isEqualTo("Choose an AI model in Settings › Providers, then start the translation.");
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
        awaitState("0 % done");
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
    }

    // IF a run under way were offered "Start translation" here, THEN the person would think a second one could begin;
    // the button says it goes back to the run, and pressing it asks nothing of the run.
    @Test
    void forward_runUnderWay_offersBackToTheRunAndOnlyShowsIt() throws Exception {
        runningThenBackToNamesStyle();

        assertThat(button("names-style-start").getText()).isEqualTo("Go to Translating");
        onFx(() -> button("names-style-start").fire());

        assertThat(engine.requests()).hasSize(1);
        assertThat(job.calls()).doesNotContain("pause", "resume");
        assertThat(currentView()).isEqualTo(ViewNames.TRANSLATING);
    }

    // IF a paused run were started again from here, THEN two jobs would work on one project; the button shows it.
    @Test
    void forward_runPaused_offersGoToTranslatingAndOnlyShowsIt() throws Exception {
        readyToStart();
        showTranslating();
        onFx(() -> button("translating-start").fire());
        job.awaitRunStarted();
        awaitState("0 % done");
        onFx(() -> button("translating-pause").fire());
        job.emit(new Paused(PauseReason.REQUESTED, null, ProgressFixtures.progress(1, 2, 5, 0, 5)));
        awaitState("Paused at");
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));

        assertThat(button("names-style-start").getText()).isEqualTo("Go to Translating");
        onFx(() -> button("names-style-start").fire());

        assertThat(engine.requests()).hasSize(1);
        assertThat(job.calls()).doesNotContain("resume");
        assertThat(currentView()).isEqualTo(ViewNames.TRANSLATING);
    }

    // IF a stopped run were offered a fresh start here, THEN Continue translation would be bypassed by a first-segment
    // run.
    @Test
    void forward_runStopped_offersGoToTranslatingNotStart() throws Exception {
        readyToStart();
        showTranslating();
        onFx(() -> button("translating-start").fire());
        job.awaitRunStarted();
        awaitState("0 % done");
        onFx(() -> button("translating-stop").fire());
        job.finish(Result.ok(cancelledReport()));
        awaitState("Stopped at");

        showNamesStyleAgain();

        assertThat(button("names-style-start").getText()).isEqualTo("Go to Translating");
        assertThat(TooltipProbe.tipText(required("names-style-start")))
                .startsWith("Opens the Translating step, where the translation can be continued");
    }

    private void showNamesStyleAgain() {
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
    }

    private TableView<?> table() {
        return (TableView<?>) required("names-style-table");
    }

    // IF a column were missing, THEN part of what the person settles could not be seen or edited.
    @Test
    void table_bookOpenEmptyGlossary_hasTheSixColumnsEndingInTheRemoveColumnAndAnEnabledStart() throws Exception {
        openBookOnly();
        showNamesStyle();

        assertThat(ThemeTestSupport.onFx(() -> table().getColumns().stream()
                        .map(column -> ((Label) column.getGraphic()).getText())
                        .toList()))
                .containsExactly("Term", "Kind", "Translation", "Gender", "Locked", "");
        assertThat(ThemeTestSupport.onFx(() -> table().getItems())).isEmpty();
        assertThat(button("names-style-start").isDisabled()).isFalse();
        assertThat(button("names-style-scan").getText()).isEqualTo("Scan");
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
        assertThat(optional("names-style-scan")).isNull();
        assertThat(optional("names-style-start")).isNull();
    }

    // IF Auto were refused at Start, THEN a book declaring no language could never be translated without a guess.
    @Test
    void startTranslation_autoSourceLanguage_asksForOneJobAndShowsTranslating() throws Exception {
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

        job.awaitRunStarted();
        assertThat(engine.requests()).hasSize(1);
        assertThat(currentView()).isEqualTo(ViewNames.TRANSLATING);
    }
}
