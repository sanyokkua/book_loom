package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.Narrator.GenderOrigin;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ScriptedSetupAssistant;

/**
 * A run can store a narrator the window never held (15i.T5); the window must pick it up when the run ends, or the first
 * brief edit afterwards saves the whole old brief over it.
 */
class StoredNarratorFollowerTest extends ExportViewModelTestBase {

    private static final Narrator INFERRED_FEMALE =
            new Narrator(NarratorPerson.FIRST, Gender.FEMALE, GenderOrigin.INFERRED, List.of());

    @TempDir
    private Path dir;

    private final ScriptedSetupAssistant assistant = new ScriptedSetupAssistant();

    @BeforeEach
    void openTheBookAndFollowTheRun() {
        openBook(dir.resolve("Frankenstein.epub"), BookFixtures.frankensteinImport());
        onFx(() -> new StoredNarratorFollower(mirror, current, assistant, new DirectExecutor()));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void runEndsAs(final RunState state) {
        mirror.publishRunState(state);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void editTheDial() {
        onFx(() -> {
            brief.setDial(QualityDial.MAX);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private Narrator lastSavedNarrator() {
        final List<BookBrief> saved = projects.briefs();
        return saved.getLast().narrator();
    }

    // IF the window kept its old brief, THEN editing the dial after the run would save the old narrator over the
    // inferred one the run stored.
    @Test
    void runEnds_storedNarratorInferred_aLaterBriefEditKeepsIt() {
        assistant.answersRecheck(Result.ok(INFERRED_FEMALE));

        runEndsAs(RunState.COMPLETED);
        editTheDial();

        assertThat(lastSavedNarrator()).isEqualTo(INFERRED_FEMALE);
        assertThat(assistant.rechecked()).containsExactly("p1");
    }

    // The person's own later choice still wins over the inferred one.
    @Test
    void runEnds_thenThePersonPicksAGender_thePersonsChoiceIsSaved() {
        assistant.answersRecheck(Result.ok(INFERRED_FEMALE));
        runEndsAs(RunState.COMPLETED);

        onFx(() -> {
            brief.setNarratorGender(Gender.MALE);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(lastSavedNarrator())
                .isEqualTo(new Narrator(NarratorPerson.FIRST, Gender.MALE, GenderOrigin.PERSON, List.of()));
    }

    @Test
    void runEnds_storedNarratorIsTheWindowsOwn_changesNothing() {
        assistant.answersRecheck(Result.ok(Narrator.unspecified()));
        final BookBrief before = onFx(() -> current.brief().get());
        final int savedBefore = projects.briefs().size();

        runEndsAs(RunState.FAILED);

        assertThat(onFx(() -> current.brief().get())).isEqualTo(before);
        assertThat(projects.briefs()).hasSize(savedBefore);
    }

    @Test
    void runPauses_doesNotLookAtTheStoredNarrator() {
        assistant.answersRecheck(Result.ok(INFERRED_FEMALE));

        runEndsAs(RunState.PAUSED);

        assertThat(assistant.rechecked()).isEmpty();
    }
}
