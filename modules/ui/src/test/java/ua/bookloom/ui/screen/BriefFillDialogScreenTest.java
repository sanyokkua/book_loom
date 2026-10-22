package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.event.Event;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.SetupAssistant;
import ua.bookloom.api.project.Register;
import ua.bookloom.ui.ScriptedSetupAssistant;

/**
 * "Fill in from the book" in the short progress dialog: its steps and their locations, and the answer held until Apply
 * (book-brief "Show the brief suggestion in a progress dialog that is not hideable", "Write nothing to the brief until
 * the person applies the suggestion").
 */
class BriefFillDialogScreenTest extends BriefRunScreenTestBase {

    private ScriptedSetupAssistant assistant;

    @BeforeEach
    void scriptAnAnswer() {
        assistant = (ScriptedSetupAssistant) injector.getInstance(SetupAssistant.class);
        assistant.answersBrief(Result.ok(BriefFillFixtures.ANSWER));
    }

    private void fillIn() throws TimeoutException {
        onFx(() -> button("brief-suggest-style").fire());
        awaitFx(this::isDialogShown);
    }

    private boolean isDialogShown() {
        return optional("busy-card") != null && optional("busy-card").isVisible();
    }

    private void awaitApplicable() throws TimeoutException {
        awaitFx(() ->
                optional("progress-apply") != null && !button("progress-apply").isDisabled());
    }

    private void escape() {
        onFx(() -> Event.fireEvent(
                scene, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false)));
    }

    @SuppressWarnings("unchecked")
    private ListView<Object> list(final String id) {
        return (ListView<Object>) required(id);
    }

    private List<String> stepTexts() {
        WaitForAsyncUtils.waitForFxEvents();
        return textsUnder(required("progress-steps"));
    }

    // IF the dialog did not stay once the work ended, THEN the person could not look at the evidence before applying.
    @Test
    void dialog_answerArrived_staysOpenWithApplyEnabled() throws Exception {
        openAndShowBrief();
        assistant.announces(BriefFillFixtures.threeOfFourCasual(true));

        fillIn();
        awaitApplicable();

        assertThat(isDialogShown()).isTrue();
        assertThat(button("progress-apply").getText()).isEqualTo("Apply to the brief");
    }

    // IF the steps were not the planned ones, THEN a person would not know what the model read.
    @Test
    void steps_balancedKnownTitle_listsKnowledgeFourSamplesWithLocationsAndSynthesis() throws Exception {
        openAndShowBrief();
        assistant.announces(BriefFillFixtures.threeOfFourCasual(true));

        fillIn();
        awaitApplicable();

        assertThat(list("progress-steps").getItems()).hasSize(6);
        assertThat(stepTexts())
                .contains(
                        "What the model knows about the book",
                        "Read sample 1 · ch1 · p02–p14",
                        "Read sample 2 · ch1 · p15–p27",
                        "Read sample 3 · ch2 · p03–p12",
                        "Read sample 4 · ch3 · p01–p20",
                        "Decide genre, readers and voice");
    }

    // The title is only the file name, so the model is not asked and the dialog lists no such step.
    @Test
    void steps_titleIsTheFileName_listsNoKnowledgeRow() throws Exception {
        openAndShowBrief();
        assistant.announces(BriefFillFixtures.threeOfFourCasual(false));

        fillIn();
        awaitApplicable();

        assertThat(list("progress-steps").getItems()).hasSize(5);
        assertThat(stepTexts()).doesNotContain("What the model knows about the book");
    }

    // IF Apply were enabled while a step runs, THEN the brief could be written from half a suggestion.
    @Test
    void apply_whileTheStepsRun_isDisabledUntilTheyHaveAllEnded() throws Exception {
        openAndShowBrief();
        assistant.announces(BriefFillFixtures.firstSampleStarted());
        assistant.holdAnswer();

        fillIn();

        assertThat(button("progress-apply").isDisabled()).isTrue();
        assistant.releaseAnswer();
        awaitApplicable();
    }

    @Test
    void footnote_suggestionDialog_saysNothingIsWrittenUntilApplied() throws Exception {
        openAndShowBrief();
        assistant.announces(BriefFillFixtures.threeOfFourCasual(true));

        fillIn();

        assertThat(textOf("progress-footnote")).isEqualTo("Nothing is written to the brief until you apply it.");
    }

    @Test
    void title_suggestionDialog_namesTheFillIn() throws Exception {
        openAndShowBrief();

        fillIn();

        assertThat(textOf("progress-title")).isEqualTo("Fill in the brief from the book");
    }

    @Test
    void button_beforeAsking_readsFillInFromTheBook() throws Exception {
        openAndShowBrief();

        assertThat(button("brief-suggest-style").getText()).isEqualTo("✦ Fill in from the book");
    }

    // IF Esc wrote the brief, THEN closing the dialog would silently change the book's settings.
    @Test
    void escape_beforeApply_closesTheDialogAndWritesNothing() throws Exception {
        openAndShowBrief();
        assistant.announces(BriefFillFixtures.threeOfFourCasual(true));
        fillIn();
        awaitApplicable();
        final int saves = projects.briefs().size();
        final String genreBefore = brief().genre();

        escape();
        awaitFx(() -> !isDialogShown());

        assertThat(projects.briefs()).hasSize(saves);
        assertThat(brief().genre()).isEqualTo(genreBefore);
        assertThat(isShown("brief-evidence-genre")).isFalse();
    }

    // IF Apply wrote through any other path, THEN the stored brief and the one on screen could differ.
    @Test
    void apply_pressed_writesOnceClosesTheDialogAndShowsTheEvidence() throws Exception {
        openAndShowBrief();
        assistant.announces(BriefFillFixtures.threeOfFourCasual(true));
        fillIn();
        awaitApplicable();
        final int saves = projects.briefs().size();

        onFx(() -> button("progress-apply").fire());
        awaitFx(() -> !isDialogShown());

        assertThat(projects.briefs()).hasSize(saves + 1);
        assertThat(brief().genre()).isEqualTo("crime");
        assertThat(brief().register()).isEqualTo(Register.CASUAL);
        assertThat(isShown("brief-evidence-genre")).isTrue();
    }

    // IF the Simple view of a sample step stayed empty, THEN a person could not see what the model read or answered.
    @Test
    void sampleStep_chosenInSimple_showsWhatWasSentAndWhatItAnswered() throws Exception {
        openAndShowBrief();
        assistant.announces(BriefFillFixtures.threeOfFourCasual(true));
        fillIn();
        awaitApplicable();

        clickOn(required("progress-step-1"));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(textOf("progress-input")).contains("ch1 · p02–p14").contains(BriefFillFixtures.QUOTE);
        assertThat(textOf("progress-output")).contains("Register: casual — “" + BriefFillFixtures.QUOTE + "”");
    }

    // IF neither mode button looked chosen, THEN a person could not tell which view the dialog was in.
    @Test
    void modeButtons_dialogShown_theChosenViewIsSelected() throws Exception {
        openAndShowBrief();
        assistant.announces(BriefFillFixtures.threeOfFourCasual(true));

        fillIn();

        assertThat(read(() -> ((javafx.scene.control.ToggleButton) required("progress-mode-simple")).isSelected()))
                .isTrue();
        assertThat(read(() -> ((javafx.scene.control.ToggleButton) required("progress-mode-detailed")).isSelected()))
                .isFalse();
    }
}
