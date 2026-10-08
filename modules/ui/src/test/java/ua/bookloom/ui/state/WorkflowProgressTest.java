package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ProgressFixtures;
import ua.bookloom.ui.ViewNames;

/** The done marks derived from the open book, its languages and the run, beyond what the steps mark themselves. */
@SuppressWarnings("NullAway.Init")
class WorkflowProgressTest extends FxTestBase {

    private CurrentProject project;
    private StateMirror mirror;
    private WorkflowProgress progress;

    @Override
    public void start(final Stage stage) {
        project = new CurrentProject();
        mirror = new StateMirror();
        progress = new WorkflowProgress(project, mirror);
    }

    private void open(final String source, final String target) {
        interact(() -> OpenBookForTest.open(project, source, target));
    }

    private void runEndedWith(final RunState state, final int accepted, final int pending) {
        mirror.publishRunStarted("Frankenstein.epub", null);
        mirror.publishProgress(ProgressFixtures.progress(1, 1, accepted, 0, pending));
        WaitForAsyncUtils.waitForFxEvents();
        mirror.publishOutcome(state, null, null);
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void briefContinuedWithValidLanguages_isDone() {
        // IF Continue did not mark the brief, THEN a finished step would stay a bare number.
        open("en", "uk");

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
        interact(() -> progress.markDone(ViewNames.STRUCTURE));
        interact(project::clear);

        assertThat(progress.done()).isEmpty();
    }
}
