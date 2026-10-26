package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.state.ProjectFilter;
import ua.bookloom.ui.state.ProjectsViewModel;

/** A filter that leaves no row says what it found none of, not that no project "matches" the filter's name. */
class ProjectsFilterEmptyTest extends ProjectsScreenTestBase {

    @Test
    void finishedFilter_noFinishedProject_saysNoFinishedTranslationsYet() throws Exception {
        showListing(ukrainianRunning());

        onFx(() -> injector.getInstance(ProjectsViewModel.class).filter().set(ProjectFilter.FINISHED));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(textOf("projects-no-match")).contains("No finished translations yet.").doesNotContain("matches");
        assertThat(isPresent("projects-clear-search")).isFalse();
    }

    @Test
    void inProgressFilter_onlyAnExportedProject_saysNoTranslationInProgress() throws Exception {
        showListing(tideExported());

        onFx(() -> injector.getInstance(ProjectsViewModel.class).filter().set(ProjectFilter.IN_PROGRESS));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(textOf("projects-no-match")).contains("No translation in progress.").doesNotContain("matches");
    }
}
