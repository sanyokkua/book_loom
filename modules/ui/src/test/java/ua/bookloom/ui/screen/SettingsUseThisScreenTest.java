package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.ProjectFixtures;
import ua.bookloom.ui.ScriptedProjectService;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.OpenBookForTest;

/** "Use this one" in the form header: when it is offered, when it is off, and the question asked mid-book. */
class SettingsUseThisScreenTest extends SettingsScreenTestBase {

    private final ScriptedProjectService projects = new ScriptedProjectService();

    @Override
    void customize(final UiTestInjector.Builder builder) {
        builder.projects(projects);
    }

    private boolean visible(final String id) {
        return ThemeTestSupport.onFx(() -> required(id).isVisible());
    }

    private void openLmStudio() {
        openSettings();
        onFx(() -> viewModel().editProvider("lmstudio"));
    }

    private void openBookWithDecisions(final int decided) {
        final var project = ProjectFixtures.project(
                "p1",
                "Frankenstein.epub",
                ProjectFixtures.BOOK_HASH,
                "en",
                "uk",
                NameOrigin.AUTO,
                "Frankenstein",
                Instant.parse("2026-10-02T10:00:00Z"));
        projects.willList(
                List.of(new ProjectSummary(project, decided, 142, ProjectFixtures.run("p1", JobState.PAUSED), null)));
        onFx(() -> OpenBookForTest.open(injector.getInstance(CurrentProject.class), "p1", "en", "uk"));
    }

    // IF the current provider offered itself, THEN the button would promise a change that does nothing.
    @Test
    void useThis_currentProviderOpen_isNotOffered() {
        openSettings();

        assertThat(visible("settings-form-use")).isFalse();
    }

    // IF another provider had no way to become current, THEN the only door would be a model chip elsewhere.
    @Test
    void useThis_noBookOpen_makesLmStudioCurrentAtOnce() throws TimeoutException {
        openLmStudio();
        assertThat(visible("settings-form-use")).isTrue();
        assertThat(ThemeTestSupport.onFx(() -> button("settings-form-use").isDisabled()))
                .isFalse();

        onFx(() -> button("settings-form-use").fire());
        awaitFx(() -> "lmstudio".equals(viewModel().selectedProviderId().get()));

        assertThat(visible("settings-form-badge")).isTrue();
        assertThat(visible("settings-form-use")).isFalse();
    }

    // IF Use this one took unsaved connection edits with it, THEN the stored provider and the choice would disagree.
    @Test
    void useThis_addressEditedNotSaved_isOffWithTheReasonBeside() {
        openLmStudio();

        onFx(() -> form().edit(draft -> draft.withAddress("http://10.0.0.5:1234/v1")));

        assertThat(ThemeTestSupport.onFx(() -> button("settings-form-use").isDisabled()))
                .isTrue();
        assertThat(visible("settings-form-use-reason")).isTrue();
        assertThat(text("settings-form-use-reason")).isEqualTo("Save or cancel your changes first.");
    }

    // IF only the model being dirty turned the button off, THEN choosing a model and using it would take a Save first.
    @Test
    void useThis_onlyTheModelTyped_staysOn() {
        openLmStudio();

        onFx(() -> form().edit(draft -> draft.withModel("qwen3:8b")));

        assertThat(ThemeTestSupport.onFx(() -> button("settings-form-use").isDisabled()))
                .isFalse();
        assertThat(visible("settings-form-use-reason")).isFalse();
    }

    // IF a book with 74 of 142 segments decided switched silently, THEN its style would change mid-book unannounced.
    @Test
    void useThis_midBook_asksWhetherToUseLmStudioForTheRest() {
        openLmStudio();
        openBookWithDecisions(74);

        askAndWait();

        assertThat(textsUnder(required("switch-provider-card")))
                .contains(
                        "Use LM Studio for the rest of this book?",
                        "This book has been translated with Ollama · gemma4:e4b so far.");
        assertThat(text("switch-provider-keep")).isEqualTo("Keep Ollama");
        assertThat(text("switch-provider-use")).isEqualTo("Use LM Studio");
    }

    // IF Keep wrote anything, THEN "keep" would not mean keep.
    @Test
    void useThis_midBookKeepPressed_leavesTheBadgeAndTheStoreAsTheyWere() {
        openLmStudio();
        openBookWithDecisions(74);
        askAndWait();

        onFx(() -> button("switch-provider-keep").fire());

        assertThat(ThemeTestSupport.onFx(() -> viewModel().selectedProviderId().get()))
                .isEqualTo("ollama");
        assertThat(visible("settings-form-badge")).isFalse();
        assertThat(store().get(AppSetting.CURRENT_MODEL).data()).isEmpty();
    }

    // IF confirming did not switch, THEN the question would be a decoration.
    @Test
    void useThis_midBookUsePressed_makesLmStudioCurrent() throws TimeoutException {
        openLmStudio();
        openBookWithDecisions(74);
        askAndWait();

        onFx(() -> button("switch-provider-use").fire());
        awaitFx(() -> "lmstudio".equals(viewModel().selectedProviderId().get()));

        assertThat(visible("settings-form-badge")).isTrue();
    }

    private void askAndWait() {
        onFx(() -> button("settings-form-use").fire());
        org.testfx.util.WaitForAsyncUtils.waitForFxEvents();
        org.testfx.util.WaitForAsyncUtils.waitForFxEvents();
    }

    private SettingsRepository store() {
        return injector.getInstance(SettingsRepository.class);
    }
}
