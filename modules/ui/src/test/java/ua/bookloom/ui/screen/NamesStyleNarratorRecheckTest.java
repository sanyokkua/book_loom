package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;
import javafx.scene.Node;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.SetupAssistant;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorHint;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ScriptedSetupAssistant;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.CurrentProject;

/**
 * Start translation checks the narrator's stored quotes against the glossary before it asks "Who narrates?" (15i.T5):
 * when that verifies the gender nobody is asked, and when it cannot, or fails, the question is asked as before.
 */
class NamesStyleNarratorRecheckTest extends TranslatingScreenTestBase {

    private static final GlossaryEntry NAME =
            new GlossaryEntry("e1", "p1", "Edwin", "Едвін", TermType.CHARACTER, Gender.MALE, false);
    private static final String CARD = "narrator-card";
    private static final Narrator VERIFIED = new Narrator(
            NarratorPerson.FIRST, Gender.MALE, Narrator.GenderOrigin.VERIFIED, List.of("Hold on tight, Edwin"));

    private ScriptedSetupAssistant assistant() {
        return (ScriptedSetupAssistant) injector.getInstance(SetupAssistant.class);
    }

    private void showNamesStyle(final NarratorHint hint) throws Exception {
        projects.on(BOOK, Result.ok(BookFixtures.withNarratorHint(BookFixtures.frankensteinImport(), hint)));
        openImport();
        openBook(BOOK);
        chooseTarget();
        ThemeTestSupport.onFx(() -> {
            injector.getInstance(ua.bookloom.ui.state.SettingsViewModel.class)
                    .model()
                    .set(MODEL);
            return null;
        });
        glossary.willAnswer(Result.ok(List.of(NAME)));
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
    }

    private boolean isAsking() {
        final Node card = scene.getRoot().lookup("#" + CARD);
        return card != null && card.isVisible() && card.getScene() != null;
    }

    private void awaitAsking() throws Exception {
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> ThemeTestSupport.onFx(this::isAsking));
    }

    private Narrator narrator() {
        return ThemeTestSupport.onFx(
                () -> injector.getInstance(CurrentProject.class).brief().get().narrator());
    }

    private String projectId() {
        return ThemeTestSupport.onFx(
                () -> injector.getInstance(CurrentProject.class).book().get().projectId());
    }

    // IF the question came first, THEN a person whose narrator the names pass just verified would still be asked.
    @Test
    void start_recheckVerifiesTheNarrator_startsWithoutAskingAndStoresIt() throws Exception {
        showNamesStyle(BookFixtures.firstPersonHint());
        assistant().answersRecheck(Result.ok(VERIFIED));

        onFx(() -> button("names-style-start").fire());

        job.awaitRunStarted();
        assertThat(isAsking()).isFalse();
        assertThat(assistant().rechecked()).containsExactly(projectId());
        assertThat(narrator()).isEqualTo(VERIFIED);
        assertThat(projects.briefs().getLast().narrator()).isEqualTo(VERIFIED);
    }

    // IF the re-check ran on the FX thread, THEN Start would freeze the window while a whole book is split.
    @Test
    void start_anyRecheck_runsOffTheFxThreadAndTheQuestionStillFollows() throws Exception {
        showNamesStyle(BookFixtures.firstPersonHint());

        onFx(() -> button("names-style-start").fire());

        awaitAsking();
        assertThat(assistant().recheckedOnFxThread()).containsExactly(false);
    }

    @Test
    void start_pressedTwiceWhileTheRecheckRuns_asksOnce() throws Exception {
        showNamesStyle(BookFixtures.firstPersonHint());

        onFx(() -> {
            button("names-style-start").fire();
            button("names-style-start").fire();
        });

        awaitAsking();
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(assistant().rechecked()).hasSize(1);
    }

    @Test
    void translatingStart_recheckVerifiesTheNarrator_startsWithoutAsking() throws Exception {
        showNamesStyle(BookFixtures.firstPersonHint());
        assistant().answersRecheck(Result.ok(VERIFIED));
        onFx(() -> shell.activate(ViewNames.TRANSLATING));

        onFx(() -> button("translating-start").fire());

        job.awaitRunStarted();
        assertThat(isAsking()).isFalse();
    }

    @Test
    void start_recheckLeavesTheGenderUnknown_asksWhoNarrates() throws Exception {
        showNamesStyle(BookFixtures.firstPersonHint());
        assistant().answersRecheck(Result.ok(new Narrator(NarratorPerson.FIRST, Gender.UNKNOWN)));

        onFx(() -> button("names-style-start").fire());

        awaitAsking();
        assertThat(assistant().rechecked()).containsExactly(projectId());
        assertThat(engine.requests()).isEmpty();
    }

    // The re-check is a convenience: a failure must not stop the run or hide the question.
    @Test
    void start_recheckFails_asksWhoNarrates() throws Exception {
        showNamesStyle(BookFixtures.firstPersonHint());
        assistant().answersRecheck(Result.err(AppError.of(ErrorCode.internal, "Failed", "The re-check failed.")));

        onFx(() -> button("names-style-start").fire());

        awaitAsking();
        assertThat(engine.requests()).isEmpty();
    }

    @Test
    void start_thirdPersonBook_doesNotRecheck() throws Exception {
        showNamesStyle(new NarratorHint(NarratorHint.Person.THIRD, 0.0, List.of(), 4));
        assistant().answersRecheck(Result.ok(VERIFIED));

        onFx(() -> button("names-style-start").fire());

        job.awaitRunStarted();
        assertThat(assistant().rechecked()).isEmpty();
    }

    @Test
    void start_genderAlreadyChosen_doesNotRecheck() throws Exception {
        showNamesStyle(BookFixtures.firstPersonHint());
        assistant().answersRecheck(Result.ok(VERIFIED));
        onFx(() -> injector.getInstance(ua.bookloom.ui.state.BookBriefViewModel.class)
                .setFirstPersonNarrator(Gender.FEMALE));

        onFx(() -> button("names-style-start").fire());

        job.awaitRunStarted();
        assertThat(assistant().rechecked()).isEmpty();
        assertThat(narrator()).isEqualTo(new Narrator(NarratorPerson.FIRST, Gender.FEMALE));
    }
}
