package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ProjectChange;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Register;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ProjectFixtures;
import ua.bookloom.ui.ScriptedProjectEdits.SecondCall;
import ua.bookloom.ui.dialog.RecordingSamePairPrompt.Asked;

/**
 * Choosing a pair another translation of the book already holds (book-brief "Open Same language pair when the chosen
 * pair is already used"): the brief shows the previous pair again, the question names the two ways on, and only "Use it
 * with this name" stores a second translation.
 */
class BookBriefSamePairScreenTest extends BriefRunScreenTestBase {

    private static final String SECOND_NAME = "Frankenstein · 26b";

    @BeforeEach
    void anotherTranslationHoldsGerman() {
        projects.willList(List.of(stored("uk", NameOrigin.AUTO, AUTOMATIC_NAME), otherTranslation("de")));
        projects.onUpdateBrief(
                Result.ok(new ProjectChange.PairInUse(otherTranslation("de").project())));
    }

    private void chooseGerman() throws Exception {
        onFx(() -> briefViewModel().setTargetLanguage("de"));
        ProjectFixtures.awaitBackgroundIdle(injector);
    }

    private String targetBox() {
        return read(() -> box("brief-target").getEditor().getText());
    }

    // IF the used pair stayed chosen on screen, THEN the brief would show a pair that was never stored.
    @Test
    void targetChosenOfAUsedPair_restoresThePreviousPairAndAsksOnce() throws Exception {
        openAndShowBrief();

        chooseGerman();

        assertThat(samePair.asked()).hasSize(1);
        assertThat(brief().targetLanguage()).isEqualTo("uk");
        assertThat(targetBox()).isEqualTo("Ukrainian");
    }

    @Test
    void question_usedPair_namesTheOtherProjectTheLanguageAndTheKeepButton() throws Exception {
        openAndShowBrief();

        chooseGerman();

        final Asked asked = samePair.asked().getFirst();
        assertThat(asked.existing().summary().project().id()).isEqualTo("p2");
        assertThat(asked.languageName()).isEqualTo("German");
        assertThat(asked.keepName()).isEqualTo("Keep Ukrainian");
        assertThat(asked.rules().automaticName()).endsWith("EN → DE");
    }

    // IF the question were asked before the service refused, THEN a free pair would be questioned for nothing.
    @Test
    void question_pairIsFree_isNeverAsked() throws Exception {
        openAndShowBrief();
        projects.onUpdateBrief(null);

        chooseGerman();

        assertThat(samePair.asked()).isEmpty();
        assertThat(brief().targetLanguage()).isEqualTo("de");
    }

    // IF a typed name made the pair used, THEN a person could not keep two translations under their own names.
    @Test
    void question_nameTypedProject_isNeverAskedBecauseTheServiceAllowsTheSharedPair() throws Exception {
        projects.willList(List.of(stored("uk", NameOrigin.USER, "Земне тяжіння")));
        projects.onUpdateBrief(null);
        readyToStart();
        showBrief();

        chooseGerman();

        assertThat(samePair.asked()).isEmpty();
        assertThat(projects.briefs().getLast().targetLanguage()).isEqualTo("de");
    }

    // The previous value was empty, so the button cannot say "Keep" and a language.
    @Test
    void question_noTargetChosenBefore_keepsTheCurrentChoice() throws Exception {
        projects.willList(List.of(stored("uk", NameOrigin.AUTO, "Frankenstein"), otherTranslation("uk")));
        projects.onUpdateBrief(
                Result.ok(new ProjectChange.PairInUse(otherTranslation("uk").project())));
        projects.on(BOOK, Result.ok(BookFixtures.frankensteinImport()));
        openImport();
        openBook(BOOK);
        showBrief();

        onFx(() -> briefViewModel().setTargetLanguage("uk"));
        ProjectFixtures.awaitBackgroundIdle(injector);

        assertThat(samePair.asked().getFirst().keepName()).isEqualTo("Keep the current choice");
    }

    // IF "Use it with this name" stored the pair without a name, THEN it would clash with the automatic one.
    @Test
    void makeSecond_nameChosen_storesBriefAndNameOnceAndKeepsTheNewPair() throws Exception {
        openAndShowBrief();
        final int savesBefore = projects.briefs().size();
        chooseGerman();
        projects.edits()
                .onMakeSecondTranslation(Result.ok(new ProjectChange.Saved(
                        stored("de", NameOrigin.USER, SECOND_NAME).project())));

        onFx(() -> samePair.makeSecond(SECOND_NAME));
        ProjectFixtures.awaitBackgroundIdle(injector);

        final List<SecondCall> seconds = projects.edits().secondTranslations();
        assertThat(seconds).hasSize(1);
        assertThat(seconds.getFirst().projectId()).isEqualTo(PROJECT_ID);
        assertThat(seconds.getFirst().name()).isEqualTo(SECOND_NAME);
        assertThat(seconds.getFirst().brief().targetLanguage()).isEqualTo("de");
        assertThat(projects.briefs()).hasSize(savesBefore + 1);
        assertThat(brief().targetLanguage()).isEqualTo("de");
    }

    @Test
    void makeSecond_nameAlreadyUsed_asksAgain() throws Exception {
        openAndShowBrief();
        chooseGerman();
        projects.edits()
                .onMakeSecondTranslation(Result.ok(
                        new ProjectChange.NameInUse(otherTranslation("de").project())));

        onFx(() -> samePair.makeSecond("Other"));
        ProjectFixtures.awaitBackgroundIdle(injector);

        assertThat(samePair.asked()).hasSize(2);
        assertThat(brief().targetLanguage()).isEqualTo("uk");
    }

    // IF "Open that project" kept the edit, THEN the other project would open with a brief nobody stored.
    @Test
    void openExisting_pressed_opensTheOtherProjectAndDiscardsTheEdit() throws Exception {
        openAndShowBrief();
        chooseGerman();

        onFx(samePair::openExisting);
        ProjectFixtures.awaitBackgroundIdle(injector);

        assertThat(opener.opened()).hasSize(1);
        assertThat(opener.opened().getFirst().summary().project().id()).isEqualTo("p2");
        assertThat(projects.edits().secondTranslations()).isEmpty();
        assertThat(brief().targetLanguage()).isEqualTo("uk");
    }

    // IF Keep (or Esc) stored anything, THEN a refused pair would still leave a trace in the project.
    @Test
    void cancelled_keepPressed_storesNothingMoreAndKeepsThePreviousPair() throws Exception {
        openAndShowBrief();
        final int savesBefore = projects.briefs().size();
        chooseGerman();

        onFx(samePair::cancel);
        ProjectFixtures.awaitBackgroundIdle(injector);

        assertThat(projects.edits().secondTranslations()).isEmpty();
        assertThat(projects.briefs()).hasSize(savesBefore + 1);
        assertThat(brief().targetLanguage()).isEqualTo("uk");
        assertThat(opener.opened()).isEmpty();
    }

    // IF the restore replaced the whole brief, THEN an edit made while the refused save was in flight would be lost.
    @Test
    void restore_registerEditedWhileTheRefusedSaveWasInFlight_keepsTheEdit() throws Exception {
        openAndShowBrief();
        projects.holdBriefSaves();
        onFx(() -> briefViewModel().setTargetLanguage("de"));
        onFx(() -> briefViewModel().setRegister(Register.CASUAL));

        projects.releaseBriefSaves();
        ProjectFixtures.awaitBackgroundIdle(injector);

        final BookBrief restored = brief();
        assertThat(restored.targetLanguage()).isEqualTo("uk");
        assertThat(restored.register()).isEqualTo(Register.CASUAL);
    }
}
