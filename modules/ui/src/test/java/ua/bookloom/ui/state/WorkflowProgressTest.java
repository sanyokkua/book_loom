package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.stage.Stage;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ProgressFixtures;
import ua.bookloom.ui.ScriptedReviewDesk;
import ua.bookloom.ui.ViewNames;

/** The done marks derived from the open book, its languages and the run, beyond what the steps mark themselves. */
@SuppressWarnings("NullAway.Init")
class WorkflowProgressTest extends FxTestBase {

    private CurrentProject project;
    private StateMirror mirror;
    private WorkflowProgress progress;
    private final ScriptedReviewDesk desk = new ScriptedReviewDesk();

    @Override
    public void start(final Stage stage) {
        project = new CurrentProject();
        mirror = new StateMirror();
        progress = new WorkflowProgress(project, mirror, desk, new DirectExecutor());
    }

    private void open(final @Nullable String source, final @Nullable String target) {
        interact(() -> OpenBookForTest.open(project, source, target));
    }

    private void runEndedWith(final RunState state, final int accepted, final int pending) {
        mirror.publishRunStarted("Frankenstein.epub", null);
        mirror.publishProgress(ProgressFixtures.progress(1, 1, accepted, 0, pending));
        WaitForAsyncUtils.waitForFxEvents();
        mirror.publishOutcome(state, null, null);
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF the marks came from this session's run only, THEN a project reopened after a restart would show its brief and
    // names as bare numbers although a run decided its segments; with nothing left, translating is done too.
    @ParameterizedTest
    @CsvSource({"2, false", "0, true"})
    void storedProjectWithDecidedSegments_marksTheBriefAndNamesAndTranslatingOnceNothingIsLeft(
            final int pending, final boolean translated) {
        desk.willAnswerCounts(new ReviewCounts(142, 130, 8, 2, 0, pending, 0, 0, 0));

        open("en", "uk");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(progress.done()).contains(ViewNames.IMPORT, ViewNames.BOOK_BRIEF, ViewNames.NAMES_STYLE);
        assertThat(progress.done().contains(ViewNames.TRANSLATING)).isEqualTo(translated);
    }

    // IF a stored project nobody ran were marked, THEN its brief would look finished before the person read it.
    @Test
    void storedProjectWithNothingDecided_marksOnlyTheImport() {
        desk.willAnswerCounts(new ReviewCounts(142, 0, 0, 0, 0, 142, 0, 0, 0));

        open("en", "uk");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(progress.done()).containsExactly(ViewNames.IMPORT);
    }

    @Test
    void briefContinuedWithValidLanguages_isDone() {
        // IF Continue did not mark the brief, THEN a finished step would stay a bare number.
        open("en", "uk");

        interact(() -> progress.markDone(ViewNames.BOOK_BRIEF));

        assertThat(progress.done()).contains(ViewNames.BOOK_BRIEF);
    }

    // IF Auto made the brief unfinishable, THEN a book declaring no language would never show its first step done.
    @Test
    void briefContinuedWithAnAutoSourceAndATarget_isDone() {
        open(null, "uk");

        interact(() -> progress.markDone(ViewNames.BOOK_BRIEF));

        assertThat(progress.done()).contains(ViewNames.BOOK_BRIEF);
    }

    @Test
    void briefContinuedThenLanguagesCleared_isNoLongerDone() {
        // IF a mark outlived the languages it vouched for, THEN a brief that cannot continue would look finished.
        open("en", "uk");
        interact(() -> progress.markDone(ViewNames.BOOK_BRIEF));

        interact(() -> project.replaceBrief(project.brief().get().withLanguages("en", null)));

        assertThat(progress.done()).doesNotContain(ViewNames.BOOK_BRIEF);
    }

    @Test
    void briefWithValidLanguagesButNeverContinued_isNotDone() {
        // IF defaults alone marked the brief, THEN it would look done before the person read it.
        open("en", "uk");

        assertThat(progress.done()).containsExactly(ViewNames.IMPORT);
    }

    @Test
    void briefWithARunThatStarted_isDone() {
        // IF a started run left the brief unmarked, THEN a book translated through the command route shows a gap.
        open("en", "uk");
        mirror.publishRunStarted("Frankenstein.epub", null);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(progress.done()).contains(ViewNames.BOOK_BRIEF, ViewNames.NAMES_STYLE);
    }

    @ParameterizedTest
    @EnumSource(
            value = RunState.class,
            names = {"STOPPED", "FAILED"})
    void translating_endedWithNothingPending_isDone(final RunState ended) {
        open("en", "uk");
        runEndedWith(ended, 10, 0);

        assertThat(progress.done()).contains(ViewNames.TRANSLATING);
    }

    @ParameterizedTest
    @EnumSource(
            value = RunState.class,
            names = {"STOPPED", "FAILED"})
    void translating_endedWithSegmentsPending_isNotDone(final RunState ended) {
        // IF a stop half way marked the step, THEN the person would believe the book was translated.
        open("en", "uk");
        runEndedWith(ended, 4, 6);

        assertThat(progress.done()).doesNotContain(ViewNames.TRANSLATING);
    }

    @Test
    void translating_stoppedBeforeAnySegmentWasCounted_isNotDone() {
        open("en", "uk");
        runEndedWith(RunState.STOPPED, 0, 0);

        assertThat(progress.done()).doesNotContain(ViewNames.TRANSLATING);
    }

    @Test
    void marks_bookChanged_dropTheExplicitOnes() {
        open("en", "uk");
        interact(() -> progress.markDone(ViewNames.BOOK_BRIEF));
        interact(project::clear);

        assertThat(progress.done()).isEmpty();
    }
}
