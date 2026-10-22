package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ProjectOpening;
import ua.bookloom.ui.ProjectFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.BookGroup;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.ImportState;
import ua.bookloom.ui.state.ImportViewModel;
import ua.bookloom.ui.state.OpenBookForTest;
import ua.bookloom.ui.state.ProjectRow;

/**
 * What each Projects control does, with recording fakes for the row actions, the file manager and the engine: the ⋯
 * menu and the book header reach the actions, Continue opens the stored project at its step and never starts a run,
 * and "+ New translation" opens Import empty while the five steps stay locked without a project.
 */
class ProjectsActionsTest extends ProjectsScreenTestBase {

    private void scriptOpens(final java.nio.file.Path source, final String projectId) {
        projects.on(source, Result.ok(ProjectFixtures.openedBook(projectId)));
    }

    private ViewNames awaitStep(final ViewNames expected) throws Exception {
        ImportScreenTestBase.awaitFx(() -> navigator.currentView().get() == expected);
        return navigator.currentView().get();
    }

    // IF a menu item reached the wrong action, THEN Remove would rename or Rename would delete; each item reaches its
    // own action with the row it was chosen on.
    @Test
    void renameItem_chosen_asksTheActionsToRenameThatRow() throws Exception {
        showListing(ukrainianRunning(), germanNamed());

        chooseMenuItem(GERMAN_ID, "Rename…");

        assertThat(actions.renamed())
                .extracting(row -> row.summary().project().id())
                .containsExactly(GERMAN_ID);
        assertThat(actions.removed()).isEmpty();
    }

    @Test
    void removeItem_chosen_asksTheActionsToRemoveThatRowAndDeletesNothingItself() throws Exception {
        showListing(ukrainianRunning(), germanNamed());

        chooseMenuItem(EARTH_ID, "Remove project…");

        assertThat(actions.removed())
                .extracting(row -> row.summary().project().id())
                .containsExactly(EARTH_ID);
        assertThat(actions.renamed()).isEmpty();
        assertThat(projects.removedProjects()).isEmpty();
    }

    // IF Translate into another language were wired to the row instead of the book, THEN the dialog would not know
    // which book's translations to copy from.
    @Test
    void translateIntoButton_pressed_asksTheActionsForThatBookGroup() throws Exception {
        showListing(ukrainianRunning(), germanNamed(), tideExported());

        press(translateIntoButton(ProjectFixtures.BOOK_HASH.substring(0, 8)));

        assertThat(actions.translatedInto()).hasSize(1);
        final BookGroup group = actions.translatedInto().getFirst();
        assertThat(group.fileName()).isEqualTo("earth-gravity.epub");
        assertThat(group.rows()).extracting(row -> row.summary().project().id()).containsExactly(EARTH_ID, GERMAN_ID);
    }

    // IF Show book file asked the file manager for another path, THEN the person would be shown a wrong folder; it is
    // the project's source, asked off the FX thread because a file manager may block.
    @Test
    void showBookFileItem_chosen_revealsTheProjectsSourceFile() throws Exception {
        showListing(ukrainianRunning());

        chooseMenuItem(EARTH_ID, "Show book file");
        ProjectFixtures.awaitBackgroundIdle(injector);

        assertThat(revealer.revealed()).containsExactly(EARTH);
    }

    // IF Continue started a run, THEN opening a project would spend the person's model time without a word; it opens
    // the stored project, shows the step the row names, and starts nothing.
    @Test
    void continue_runningProject_opensItShowsTranslatingAndStartsNoRun() throws Exception {
        scriptOpens(EARTH, "p1");
        showListing(ukrainianRunning());

        press(continueButton(EARTH_ID));
        awaitStep(ViewNames.TRANSLATING);
        ProjectFixtures.awaitBackgroundIdle(injector);

        assertThat(projects.opens()).containsExactly(EARTH);
        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.TRANSLATING);
        assertThat(engine.requests()).isEmpty();
        assertThat(ThemeTestSupport.onFx(
                        () -> injector.getInstance(CurrentProject.class).book().get()))
                .isNotNull();
        assertThat(((Label) node("shell-run-project")).getText()).contains("Earth Gravity · EN → UK");
        assertThat(projects.listCallsOnFxThread()).doesNotContain(true);
    }

    // IF a project that was never run opened on Translating, THEN the person would skip the brief; it opens on Book
    // Brief.
    @Test
    void continue_neverRunProject_opensOnBookBrief() throws Exception {
        scriptOpens(TIDE, "p1");
        showListing(tideNotStarted());

        press(continueButton(TIDE_ID));

        assertThat(awaitStep(ViewNames.BOOK_BRIEF)).isEqualTo(ViewNames.BOOK_BRIEF);
        assertThat(engine.requests()).isEmpty();
    }

    @Test
    void continue_exportedProject_opensOnExport() throws Exception {
        scriptOpens(TIDE, "p1");
        showListing(tideExported());

        press(continueButton(TIDE_ID));

        assertThat(awaitStep(ViewNames.EXPORT)).isEqualTo(ViewNames.EXPORT);
        assertThat(engine.requests()).isEmpty();
    }

    // IF a missing or changed book file opened the project anyway, THEN the screens would work on text that is not
    // there; the row asks the actions to find the book and nothing opens.
    @Test
    void continue_bookFileMissing_asksTheActionsToFindTheBookAndOpensNothing() throws Exception {
        final ProjectOpening missing =
                new ProjectOpening.BookFileMissing(ukrainianRunning().project());
        projects.onOpen(EARTH, Result.ok(missing));
        showListing(ukrainianRunning());

        press(continueButton(EARTH_ID));
        ProjectFixtures.awaitBackgroundIdle(injector);

        assertThat(actions.findBookRows())
                .extracting(row -> row.summary().project().id())
                .containsExactly(EARTH_ID);
        assertThat(actions.findBookOpenings()).containsExactly(missing);
        assertThat(ThemeTestSupport.onFx(
                        () -> injector.getInstance(CurrentProject.class).book().get()))
                .isNull();
        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.PROJECTS);
    }

    @Test
    void continue_bookFileChanged_asksTheActionsToFindTheBookAndOpensNothing() throws Exception {
        final ProjectOpening changed =
                new ProjectOpening.BookFileChanged(ukrainianRunning().project());
        projects.onOpen(EARTH, Result.ok(changed));
        showListing(ukrainianRunning());

        press(continueButton(EARTH_ID));
        ProjectFixtures.awaitBackgroundIdle(injector);

        assertThat(actions.findBookOpenings()).containsExactly(changed);
        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.PROJECTS);
    }

    // IF "+ New translation" were refused for want of a project, THEN the first book could never be imported; it opens
    // the empty drop zone while the other four steps stay locked.
    @Test
    void newTranslation_noProjectOpen_showsAnIdleImportAndKeepsTheBriefLocked() throws Exception {
        showListing(ukrainianRunning());

        press((Button) node("projects-new"));

        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.IMPORT);
        assertThat(isPresent("import-dropzone")).isTrue();
        assertThat(ThemeTestSupport.onFx(() ->
                        injector.getInstance(ImportViewModel.class).state().get()))
                .isInstanceOf(ImportState.Idle.class);
        assertThat(required("nav-book-brief").getStyleClass()).contains("nav-item-locked");
        assertThat(ThemeTestSupport.onFx(() -> navigator.navigate(ViewNames.BOOK_BRIEF)))
                .isFalse();
    }

    @Test
    void emptyStateNewTranslation_pressed_showsAnIdleImport() throws Exception {
        showEmptyListing();

        press((Button) node("projects-empty-new"));

        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.IMPORT);
        assertThat(isPresent("import-dropzone")).isTrue();
    }

    // IF "+ New translation" closed the open project, THEN a person who changed their mind would lose their place.
    @Test
    void newTranslation_projectOpen_showsAnIdleImportAndTheProjectStaysOpen() throws Exception {
        onFx(() -> OpenBookForTest.open(injector.getInstance(CurrentProject.class), "en", "uk"));
        showListing(ukrainianRunning());

        press((Button) node("projects-new"));

        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.IMPORT);
        assertThat(ThemeTestSupport.onFx(() ->
                        injector.getInstance(ImportViewModel.class).state().get()))
                .isInstanceOf(ImportState.Idle.class);
        assertThat(ThemeTestSupport.onFx(
                        () -> injector.getInstance(CurrentProject.class).book().get()))
                .isNotNull();
    }

    // IF leaving the empty Import left a ghost project open, THEN the Projects list would show the first-launch card
    // beside a title bar naming nothing real.
    @Test
    void importOpenedEmpty_leftWithoutABook_projectsStillShowsTheFirstLaunchCard() throws Exception {
        showEmptyListing();
        press((Button) node("projects-empty-new"));

        onFx(() -> navigator.navigate(ViewNames.PROJECTS));
        ImportScreenTestBase.awaitFx(() -> find("projects-empty-card") != null);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.PROJECTS);
        assertThat(ThemeTestSupport.onFx(
                        () -> injector.getInstance(CurrentProject.class).book().get()))
                .isNull();
        assertThat(isPresent("projects-empty-card")).isTrue();
    }

    @Test
    void rowRecords_ofTheListing_areTheOnesTheActionsReceive() throws Exception {
        showListing(germanNamed());

        chooseMenuItem(GERMAN_ID, "Rename…");

        final ProjectRow row = actions.renamed().getFirst();
        assertThat(row.summary()).isEqualTo(germanNamed());
    }
}
