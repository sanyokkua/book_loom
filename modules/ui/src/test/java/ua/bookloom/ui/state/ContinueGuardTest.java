package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.google.inject.Injector;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.RunRecord;
import ua.bookloom.ui.ProjectFixtures;
import ua.bookloom.ui.ScriptedModelCatalog;
import ua.bookloom.ui.ScriptedProjectService;
import ua.bookloom.ui.ShellTestBase;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.dialog.RecordingContinuePrompt;
import ua.bookloom.ui.dialog.RecordingContinuePrompt.Asked;

/**
 * What a continued run asks before it uses a model: the stored model of the project's latest run against the chosen
 * one, then whether the model to use answers. The guard, the navigator and the project holder are the window's real
 * ones; the stored run, the model listing and the two questions are hand-written fakes, and background work runs on
 * the calling thread.
 */
class ContinueGuardTest extends ShellTestBase {

    private static final ModelSelection STORED = new ModelSelection("ollama", "gemma4:e4b");
    private static final ModelSelection CHOSEN = new ModelSelection("ollama", "qwen3:8b");
    private static final String PROJECT = "p1";

    private final ScriptedProjectService projects = new ScriptedProjectService();
    private final ScriptedModelCatalog catalog = ScriptedModelCatalog.idle();
    private final RecordingContinuePrompt prompt = new RecordingContinuePrompt();
    private final List<ModelSelection> proceeded = new CopyOnWriteArrayList<>();
    private final AtomicInteger cancelled = new AtomicInteger();

    @Override
    protected Injector createInjector(final Locale locale) {
        return UiTestInjector.builder(locale)
                .projects(projects)
                .catalog(catalog)
                .continuePrompt(prompt)
                .backgroundExecutor(new DirectExecutor())
                .build();
    }

    private void latestRunWith(final @Nullable RunRecord run) {
        projects.willList(List.of(ProjectFixtures.summary(
                ProjectFixtures.project(
                        PROJECT,
                        "earth-gravity.epub",
                        ProjectFixtures.BOOK_HASH,
                        "en",
                        "uk",
                        NameOrigin.AUTO,
                        "Earth Gravity",
                        Instant.parse("2026-10-21T10:00:00Z")),
                3,
                run,
                null)));
    }

    private void storedModelIs(final ModelSelection model) {
        latestRunWith(runWith(model));
    }

    private static RunRecord runWith(final ModelSelection model) {
        final RunRecord base = ProjectFixtures.run(PROJECT, JobState.PAUSED);
        return new RunRecord(
                base.runId(),
                base.projectId(),
                base.startedAt(),
                base.endedAt(),
                base.state(),
                base.accepted(),
                base.flagged(),
                model);
    }

    private void confirm(final ModelSelection current) {
        onFx(() -> {
            OpenBookForTest.open(injector.getInstance(CurrentProject.class), "en", "uk");
            injector.getInstance(ContinueGuard.class).confirm(current, proceeded::add, cancelled::incrementAndGet);
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void answer(final Runnable press) {
        onFx(() -> {
            press.run();
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF the answer test ran before the different-model question, THEN the person would be told a model is silent that
    // they were about to replace.
    @Test
    void confirm_storedModelDiffersFromTheChosenOne_asksFirstAndTestsNothingYet() {
        storedModelIs(STORED);

        confirm(CHOSEN);

        assertThat(prompt.asked()).containsExactly(new Asked(RecordingContinuePrompt.DIFFERENT, STORED, CHOSEN));
        assertThat(catalog.callCount()).isZero();
        assertThat(proceeded).isEmpty();
        assertThat(cancelled.get()).isZero();
    }

    @Test
    void useEarlier_modelAnswers_continuesWithTheEarlierModel() {
        storedModelIs(STORED);
        catalog.respondWith("gemma4:e4b", "qwen3:8b");
        confirm(CHOSEN);

        answer(prompt::useEarlier);

        assertThat(proceeded).containsExactly(STORED);
        assertThat(catalog.providerIds()).containsExactly("ollama");
        assertThat(prompt.kinds()).containsExactly(RecordingContinuePrompt.DIFFERENT);
    }

    @Test
    void useNew_modelAnswers_continuesWithTheChosenModel() {
        storedModelIs(STORED);
        catalog.respondWith("gemma4:e4b", "qwen3:8b");
        confirm(CHOSEN);

        answer(prompt::useNew);

        assertThat(proceeded).containsExactly(CHOSEN);
        assertThat(cancelled.get()).isZero();
    }

    // IF Escape counted as a choice, THEN closing the card would start a run with a model the person never picked.
    @Test
    void escape_onTheDifferentModelQuestion_startsNothingAndCancels() {
        storedModelIs(STORED);
        confirm(CHOSEN);

        answer(prompt::escape);

        assertThat(proceeded).isEmpty();
        assertThat(cancelled.get()).isEqualTo(1);
        assertThat(prompt.asked()).hasSize(1);
        assertThat(catalog.callCount()).isZero();
    }

    @Test
    void useEarlier_earlierModelSilent_asksNotAnsweringAboutTheEarlierModel() {
        storedModelIs(STORED);
        catalog.respondWith(AppError.of(ErrorCode.unreachable, "Down", "Nothing answers."));
        confirm(CHOSEN);

        answer(prompt::useEarlier);

        assertThat(prompt.asked())
                .extracting(Asked::kind, Asked::first)
                .containsExactly(
                        tuple(RecordingContinuePrompt.DIFFERENT, STORED),
                        tuple(RecordingContinuePrompt.NOT_ANSWERING, STORED));
        assertThat(proceeded).isEmpty();
    }

    @Test
    void confirm_sameModelAnswering_asksNothingAndContinuesWithIt() {
        storedModelIs(STORED);
        catalog.respondWith("gemma4:e4b");

        confirm(STORED);

        assertThat(prompt.asked()).isEmpty();
        assertThat(proceeded).containsExactly(STORED);
        assertThat(catalog.providerIds()).containsExactly("ollama");
    }

    // IF a program that lists no models counted as silent, THEN a server that hides its list could never continue.
    @Test
    void confirm_sameModelProgramListsNoModels_countsAsAnswering() {
        storedModelIs(STORED);

        confirm(STORED);

        assertThat(prompt.asked()).isEmpty();
        assertThat(proceeded).containsExactly(STORED);
    }

    // IF the model were only checked to be installed by name, THEN a listing without it would start a run that fails.
    @Test
    void confirm_listingLacksTheModelAmongOthers_asksNotAnswering() {
        storedModelIs(STORED);
        catalog.respondWith("qwen3:8b");

        confirm(STORED);

        assertThat(prompt.kinds()).containsExactly(RecordingContinuePrompt.NOT_ANSWERING);
        assertThat(proceeded).isEmpty();
    }

    // IF any listing failure but "unreachable" slipped through, THEN a registry error would start a run with no model.
    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {"unreachable", "timeout", "validation", "discoveryFailed", "auth"})
    void confirm_listingFails_asksNotAnsweringAndStartsNothing(final ErrorCode code) {
        storedModelIs(STORED);
        catalog.respondWith(AppError.of(code, "Failed", "The listing failed."));

        confirm(STORED);

        assertThat(prompt.asked()).containsExactly(new Asked(RecordingContinuePrompt.NOT_ANSWERING, STORED, null));
        assertThat(proceeded).isEmpty();
        assertThat(cancelled.get()).isZero();
    }

    @Test
    void notNow_onTheNotAnsweringQuestion_startsNothingAndCancels() {
        storedModelIs(STORED);
        catalog.respondWith(AppError.of(ErrorCode.unreachable, "Down", "Nothing answers."));
        confirm(STORED);

        answer(prompt::notNow);

        assertThat(proceeded).isEmpty();
        assertThat(cancelled.get()).isEqualTo(1);
    }

    // IF Try again repeated the whole look, THEN it would save providers and reset a model the person chose; it only
    // asks the one model again.
    @Test
    void tryAgain_modelAnswersNow_continuesWithoutAskingAgainOrLookingForPrograms() {
        storedModelIs(STORED);
        catalog.respondWith(AppError.of(ErrorCode.unreachable, "Down", "Nothing answers."));
        confirm(STORED);
        catalog.respondWith("gemma4:e4b");

        answer(prompt::tryAgain);

        assertThat(proceeded).containsExactly(STORED);
        assertThat(prompt.kinds()).containsExactly(RecordingContinuePrompt.NOT_ANSWERING);
        assertThat(catalog.providerIds()).containsExactly("ollama", "ollama");
    }

    @Test
    void tryAgain_modelStillSilent_asksNotAnsweringAgain() {
        storedModelIs(STORED);
        catalog.respondWith(AppError.of(ErrorCode.unreachable, "Down", "Nothing answers."));
        confirm(STORED);

        answer(prompt::tryAgain);

        assertThat(prompt.kinds())
                .containsExactly(RecordingContinuePrompt.NOT_ANSWERING, RecordingContinuePrompt.NOT_ANSWERING);
        assertThat(proceeded).isEmpty();
        assertThat(cancelled.get()).isZero();
    }

    // IF "Use another AI model…" left the run waiting, THEN the Translating screen would stay busy behind Settings.
    @Test
    void useAnother_onTheNotAnsweringQuestion_opensSettingsStartsNothingAndCancels() {
        storedModelIs(STORED);
        catalog.respondWith(AppError.of(ErrorCode.unreachable, "Down", "Nothing answers."));
        confirm(STORED);

        answer(prompt::useAnother);

        assertThat(ThemeTestSupport.onFx(() -> navigator.currentView().get())).isEqualTo(ViewNames.SETTINGS);
        assertThat(proceeded).isEmpty();
        assertThat(cancelled.get()).isEqualTo(1);
    }

    @Test
    void confirm_noLatestRun_asksNothingTestsNothingAndContinuesWithTheChosenModel() {
        latestRunWith(null);

        confirm(CHOSEN);

        assertThat(prompt.asked()).isEmpty();
        assertThat(catalog.callCount()).isZero();
        assertThat(proceeded).containsExactly(CHOSEN);
    }

    @Test
    void confirm_projectNotListed_asksNothingAndContinuesWithTheChosenModel() {
        projects.willList(List.of());

        confirm(CHOSEN);

        assertThat(prompt.asked()).isEmpty();
        assertThat(proceeded).containsExactly(CHOSEN);
    }

    @Test
    void confirm_latestRunKeptNoModel_asksNothingAndContinuesWithTheChosenModel() {
        final RunRecord base = ProjectFixtures.run(PROJECT, JobState.PAUSED);
        latestRunWith(new RunRecord(
                base.runId(),
                base.projectId(),
                base.startedAt(),
                base.endedAt(),
                base.state(),
                base.accepted(),
                base.flagged(),
                null));

        confirm(CHOSEN);

        assertThat(prompt.asked()).isEmpty();
        assertThat(proceeded).containsExactly(CHOSEN);
    }

    // IF the stored model came from the row the screen drew earlier, THEN a run the person started elsewhere meanwhile
    // would be compared with stale data; it is read when Continue is pressed.
    @Test
    void confirm_called_readsTheProjectListAtThatMoment() {
        storedModelIs(STORED);
        final int before = projects.listCalls();

        confirm(STORED);

        assertThat(projects.listCalls()).isGreaterThan(before);
    }
}
