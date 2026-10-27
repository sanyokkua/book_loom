package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.MemorySettings;
import ua.bookloom.ui.ProjectFixtures;
import ua.bookloom.ui.ScriptedProjectService;
import ua.bookloom.ui.ShellTestBase;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.ViewNames;

/**
 * At launch the window opens the last project when the setting is on and that project is still stored, and otherwise
 * stays on Projects; the real settings round trip, the scripted project service and the real navigator.
 */
class LaunchProjectTest extends ShellTestBase {

    private static final String LAST = ProjectFixtures.idOf(ProjectFixtures.BOOK_FILE);

    private final MemorySettings settings = new MemorySettings();
    private final ScriptedProjectService projects = new ScriptedProjectService();

    @Override
    protected Injector createInjector(final Locale locale) {
        return UiTestInjector.builder(locale)
                .projects(projects)
                .settings(settings)
                .gated()
                .build();
    }

    private static ProjectSummary stored(final String id, final String file) {
        return ProjectFixtures.summary(
                ProjectFixtures.project(
                        id,
                        file,
                        ProjectFixtures.BOOK_HASH,
                        "en",
                        "uk",
                        NameOrigin.AUTO,
                        "Earth Gravity · EN → UK",
                        UiTestInjector.NOW),
                100,
                ProjectFixtures.run(id, JobState.PAUSED),
                null);
    }

    private void launch() throws Exception {
        projects.on(ProjectFixtures.source(ProjectFixtures.BOOK_FILE), Result.ok(ProjectFixtures.openedBook("p1")));
        onFx(() -> shell.activate(ViewNames.PROJECTS));
        onFx(() -> injector.getInstance(LaunchProject.class).openAtLaunch());
        ProjectFixtures.awaitBackgroundIdle(injector);
    }

    // IF the launch ignored the stored id, THEN "Reopen the last project" would do nothing; it opens that project at
    // the step its row names and starts no run.
    @Test
    void openAtLaunch_switchOnAndProjectStored_opensItAtItsStep() throws Exception {
        settings.put(AppSetting.REOPEN_LAST_PROJECT, true);
        settings.put(AppSetting.LAST_PROJECT, LAST);
        projects.willList(List.of(stored(LAST, ProjectFixtures.BOOK_FILE)));

        launch();

        assertThat(projects.opens()).containsExactly(ProjectFixtures.source(ProjectFixtures.BOOK_FILE));
        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.TRANSLATING);
        assertThat(ThemeTestSupport.onFx(
                        () -> injector.getInstance(CurrentProject.class).book().get()))
                .isNotNull();
    }

    // IF the switch were ignored, THEN a person who turned the reopening off would still land in a book.
    @Test
    void openAtLaunch_switchOff_staysOnProjectsAndOpensNothing() throws Exception {
        settings.put(AppSetting.REOPEN_LAST_PROJECT, false);
        settings.put(AppSetting.LAST_PROJECT, LAST);
        projects.willList(List.of(stored(LAST, ProjectFixtures.BOOK_FILE)));

        launch();

        assertThat(projects.opens()).isEmpty();
        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.PROJECTS);
    }

    // IF a removed project were opened anyway, THEN the launch would show an error for a choice the person made long
    // ago; it quietly stays on Projects.
    @Test
    void openAtLaunch_lastProjectNoLongerStored_staysOnProjectsAndOpensNothing() throws Exception {
        settings.put(AppSetting.REOPEN_LAST_PROJECT, true);
        settings.put(AppSetting.LAST_PROJECT, "gone");
        projects.willList(List.of(stored(LAST, ProjectFixtures.BOOK_FILE)));

        launch();

        assertThat(projects.opens()).isEmpty();
        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.PROJECTS);
    }

    @Test
    void openAtLaunch_noLastProjectStored_staysOnProjectsAndOpensNothing() throws Exception {
        settings.put(AppSetting.REOPEN_LAST_PROJECT, true);
        projects.willList(List.of(stored(LAST, ProjectFixtures.BOOK_FILE)));

        launch();

        assertThat(projects.opens()).isEmpty();
        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.PROJECTS);
    }

    // IF a click on the list that beat the launch were overwritten by it, THEN the person would be moved off the
    // project they just chose.
    @Test
    void openAtLaunch_aProjectOpenedMeanwhile_isLeftAlone() throws Exception {
        settings.put(AppSetting.REOPEN_LAST_PROJECT, true);
        settings.put(AppSetting.LAST_PROJECT, LAST);
        projects.willList(List.of(stored(LAST, ProjectFixtures.BOOK_FILE)));
        onFx(() -> OpenBookForTest.open(injector.getInstance(CurrentProject.class), "en", "uk"));

        launch();

        assertThat(projects.opens()).isEmpty();
    }
}
