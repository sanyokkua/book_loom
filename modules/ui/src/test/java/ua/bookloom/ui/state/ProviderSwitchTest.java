package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.ProjectFixtures;
import ua.bookloom.ui.ScriptedProjectService;
import ua.bookloom.ui.dialog.RecordingSwitchPrompt;

/** Use this one asks first only when the open book has translated segments, and keeping changes nothing. */
class ProviderSwitchTest extends ProviderFormTestBase {

    private final ScriptedProjectService projects = new ScriptedProjectService();
    private final RecordingSwitchPrompt prompt = new RecordingSwitchPrompt();
    private final CurrentProject current = new CurrentProject();

    private @Nullable SettingsViewModel viewModel;
    private @Nullable ProviderSwitch provider;

    private SettingsViewModel model() {
        return Objects.requireNonNull(viewModel);
    }

    private ProviderSwitch switcher() {
        return Objects.requireNonNull(provider);
    }

    private void openLmStudioAfterOllamaWithGemma(final boolean bookOpen) {
        openLmStudioAfterOllamaWithGemma(bookOpen, executor);
    }

    private void openLmStudioAfterOllamaWithGemma(final boolean bookOpen, final ExecutorService switchExecutor) {
        final SettingsViewModel built = viewModel();
        viewModel = built;
        final ProviderForm form = form(built);
        onFx(() -> {
            built.adopt(new ModelSelection("ollama", "gemma4:e4b"));
            built.editProvider("lmstudio");
            if (bookOpen) {
                OpenBookForTest.open(current, "p1", "en", "uk");
            }
            return null;
        });
        edit(form, draft -> draft.withModel("qwen3:8b"));
        provider = onFx(() -> new ProviderSwitch(projects, current, built, form, prompt, switchExecutor));
    }

    private void summary(final int decided, final boolean withRun) {
        final Project project = ProjectFixtures.project(
                "p1",
                "Frankenstein.epub",
                ProjectFixtures.BOOK_HASH,
                "en",
                "uk",
                NameOrigin.AUTO,
                "Frankenstein",
                Instant.parse("2026-10-02T10:00:00Z"));
        projects.willList(List.of(new ProjectSummary(
                project, decided, 142, withRun ? ProjectFixtures.run("p1", JobState.PAUSED) : null, null)));
    }

    private void press() {
        onFx(() -> {
            switcher().use();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private String currentProvider() {
        return onFx(() -> model().selectedProviderId().get());
    }

    // IF the question came with no book open, THEN choosing a provider would ask about work that does not exist.
    @Test
    void use_noBookOpen_switchesAtOnceAndAsksNothing() {
        openLmStudioAfterOllamaWithGemma(false);

        press();

        assertThat(prompt.asked()).isEmpty();
        assertThat(currentProvider()).isEqualTo("lmstudio");
        assertThat(settings.get(AppSetting.CURRENT_MODEL).data()).contains(new ModelSelection("lmstudio", "qwen3:8b"));
    }

    // IF a book with 74 of 142 segments decided switched silently, THEN its style would change mid-book unannounced.
    @Test
    void use_seventyFourOfOneHundredFortyTwoDecided_asksNamingTheEarlierModelAndChangesNothingYet() {
        openLmStudioAfterOllamaWithGemma(true);
        summary(74, true);

        press();

        assertThat(prompt.asked())
                .containsExactly(new RecordingSwitchPrompt.Asked("LM Studio", "Ollama", "gemma4:e4b"));
        assertThat(currentProvider()).isEqualTo("ollama");
        assertThat(settings.get(AppSetting.CURRENT_MODEL).data()).isEmpty();
    }

    // IF Keep wrote anything, THEN "keep" would not mean keep.
    @Test
    void use_keepAnswered_leavesTheChoiceAndTheStoreAsTheyWere() {
        openLmStudioAfterOllamaWithGemma(true);
        summary(74, true);
        press();

        prompt.keep();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(currentProvider()).isEqualTo("ollama");
        assertThat(onFx(() -> model().model().get())).isEqualTo("gemma4:e4b");
        assertThat(settings.get(AppSetting.CURRENT_MODEL).data()).isEmpty();
    }

    // IF confirming did not switch, THEN the question would be a decoration.
    @Test
    void use_confirmed_makesTheOpenProviderCurrent() {
        openLmStudioAfterOllamaWithGemma(true);
        summary(74, true);
        press();

        onFx(() -> {
            prompt.use();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(currentProvider()).isEqualTo("lmstudio");
        assertThat(settings.get(AppSetting.CURRENT_MODEL).data()).contains(new ModelSelection("lmstudio", "qwen3:8b"));
    }

    // IF an open book with nothing decided asked, THEN a fresh book would be questioned for no reason.
    @Test
    void use_openBookWithNothingDecided_switchesWithoutAsking() {
        openLmStudioAfterOllamaWithGemma(true);
        summary(0, false);

        press();

        assertThat(prompt.asked()).isEmpty();
        assertThat(currentProvider()).isEqualTo("lmstudio");
    }

    // IF a book without a run record named no earlier model, THEN the question would have nothing to compare.
    @Test
    void use_decisionsButNoRunRecord_namesTheCurrentSelectionAsTheEarlierModel() {
        openLmStudioAfterOllamaWithGemma(true);
        summary(12, false);

        press();

        assertThat(prompt.asked())
                .containsExactly(new RecordingSwitchPrompt.Asked("LM Studio", "Ollama", "gemma4:e4b"));
    }

    // IF a failed read were taken for "nothing decided", THEN a model could be switched under a half-done book.
    @Test
    void use_decisionsCannotBeRead_asksAnyway() {
        openLmStudioAfterOllamaWithGemma(true);
        projects.failDecidedWith(AppError.of(ErrorCode.internal, "Not read", "The store refused."));

        press();

        assertThat(prompt.asked()).hasSize(1);
        assertThat(currentProvider()).isEqualTo("ollama");
    }

    // IF a second press while the decisions are being counted asked again, THEN two questions would stack.
    @Test
    void use_pressedTwiceWhileCounting_asksOnce() {
        openLmStudioAfterOllamaWithGemma(true);
        summary(74, true);

        onFx(() -> {
            switcher().use();
            switcher().use();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(prompt.asked()).hasSize(1);
    }

    // IF the answer used whatever row is open when the count arrives, THEN a person who moved on would switch to a
    // provider they did not choose.
    @Test
    void use_anotherRowOpenedWhileCounting_appliesTheOneThatWasChosen() {
        openLmStudioAfterOllamaWithGemma(true);
        summary(74, true);

        onFx(() -> {
            switcher().use();
            model().editProvider("ollama");
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitForFxEvents();
        onFx(() -> {
            prompt.use();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(prompt.asked().get(0).chosenName()).isEqualTo("LM Studio");
        assertThat(currentProvider()).isEqualTo("lmstudio");
        assertThat(settings.get(AppSetting.CURRENT_MODEL).data()).contains(new ModelSelection("lmstudio", "qwen3:8b"));
    }

    // IF the project service were asked on the FX thread, THEN a slow disk would freeze the window.
    @Test
    void use_openBook_readsTheDecisionsOffTheFxThread() throws TimeoutException {
        final ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            openLmStudioAfterOllamaWithGemma(true, pool);
            summary(74, true);

            onFx(() -> {
                switcher().use();
                return null;
            });
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> projects.listCalls() > 0);
            WaitForAsyncUtils.waitForFxEvents();

            assertThat(projects.listCallsOnFxThread()).isNotEmpty().doesNotContain(true);
        } finally {
            pool.shutdownNow();
        }
    }
}
