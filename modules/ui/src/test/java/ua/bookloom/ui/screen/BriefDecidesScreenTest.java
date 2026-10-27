package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.ListView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.CallSnapshotUpdated;
import ua.bookloom.api.pipeline.FieldEvidence;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.SetupAssistant;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.ui.ScriptedSetupAssistant;
import ua.bookloom.ui.state.ProgressDialogDefaults;
import ua.bookloom.ui.state.ProgressDialogModel;

/**
 * "What it decides" in the fill-in dialog: one row per field of the brief the suggestion decides, with the agreement of
 * the samples and, on selecting a row, each sample's answer beside the quote from the book (book-brief "Show what the
 * suggestion decides with its agreement and verified quote").
 */
class BriefDecidesScreenTest extends BriefRunScreenTestBase {

    private ScriptedSetupAssistant assistant;

    @BeforeEach
    void scriptAnAnswer() {
        assistant = (ScriptedSetupAssistant) injector.getInstance(SetupAssistant.class);
        assistant.answersBrief(Result.ok(BriefFillFixtures.ANSWER));
    }

    private void fillIn() throws TimeoutException {
        onFx(() -> button("brief-suggest-style").fire());
        awaitFx(() -> optional("busy-card") != null && optional("busy-card").isVisible());
    }

    private void awaitApplicable() throws TimeoutException {
        awaitFx(() ->
                optional("progress-apply") != null && !button("progress-apply").isDisabled());
    }

    @SuppressWarnings("unchecked")
    private ListView<Object> decisions() {
        return (ListView<Object>) required("progress-decisions");
    }

    private void selectDecision(final int index) {
        onFx(() -> decisions().getSelectionModel().select(index));
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF the rows were not the four of the mockup, THEN a field would be decided that the person was never shown.
    @Test
    void decides_answerArrived_listsTheFourFieldsUnderTheirGroup() throws Exception {
        openAndShowBrief();
        assistant.announces(BriefFillFixtures.threeOfFourCasual(true));

        fillIn();
        awaitApplicable();

        assertThat(decisions().getItems()).hasSize(4);
        assertThat(textOf("progress-decisions"))
                .contains("Genre", "Register", "Who tells the story", "Who reads it · voice");
        assertThat(textOf("busy-card")).contains("What it decides").contains("Steps");
    }

    @Test
    void decides_registerAgreedByThreeOfFour_showsItsValueAndTheAgreement() throws Exception {
        openAndShowBrief();
        assistant.announces(BriefFillFixtures.threeOfFourCasual(true));

        fillIn();
        awaitApplicable();

        assertThat(textOf("progress-decisions")).contains("Casual").contains("3 of 4 samples agree");
    }

    // IF selecting a row showed nothing, THEN the person could not check the quote a value rests on.
    @Test
    void decides_registerRowSelected_listsEachSampleWithItsLocationAnswerAndQuote() throws Exception {
        openAndShowBrief();
        assistant.announces(BriefFillFixtures.threeOfFourCasual(true));
        fillIn();
        awaitApplicable();

        selectDecision(1);

        assertThat(textOf("progress-sample-grid"))
                .contains("Sample", "Answer", "Quote from the book")
                .contains("ch1 · p02–p14", "ch3 · p01–p20")
                .contains(BriefFillFixtures.QUOTE);
    }

    // IF a value without a quote counted, THEN a guess would look like a reading of the book.
    @Test
    void decides_sampleAnswerWithoutAQuote_isShownNotUsed() throws Exception {
        openAndShowBrief();
        assistant.announces(BriefFillFixtures.balancedRun(
                true,
                List.of("casual", "casual", "casual", "casual"),
                List.of(BriefFillFixtures.QUOTE, "", BriefFillFixtures.QUOTE, BriefFillFixtures.QUOTE)));
        fillIn();
        awaitApplicable();

        selectDecision(1);

        assertThat(textOf("progress-sample-grid")).contains("no quote — not used");
    }

    // IF a split looked like a decision, THEN the brief would carry a coin toss as a reading of the book.
    @Test
    void decides_registerSplitTwoAndTwo_isMarkedUncertain() throws Exception {
        openAndShowBrief();
        assistant.answersBrief(Result.ok(new BriefSuggestion(
                null,
                Register.NEUTRAL,
                null,
                null,
                NarratorPerson.UNSPECIFIED,
                Gender.UNKNOWN,
                Map.of(BriefField.REGISTER, new FieldEvidence(FieldEvidence.Basis.UNCERTAIN, List.of(), 2, 4)))));
        assistant.announces(BriefFillFixtures.balancedRun(
                true,
                List.of("casual", "casual", "neutral", "neutral"),
                List.of(
                        BriefFillFixtures.QUOTE,
                        BriefFillFixtures.QUOTE,
                        BriefFillFixtures.QUOTE,
                        BriefFillFixtures.QUOTE)));

        fillIn();
        awaitApplicable();

        assertThat(textOf("progress-decisions")).contains("uncertain").contains("2 of 4 samples agree");
    }

    // While the last step still runs, the tally is shown as it stands, marked so far.
    @Test
    void decides_beforeTheAnswer_showsTheTallySoFar() throws Exception {
        openAndShowBrief();
        assistant.announces(BriefFillFixtures.threeOfFourCasual(true));
        assistant.holdAnswer();

        fillIn();
        awaitFx(() -> optional("progress-decisions") != null
                && textOf("progress-decisions").contains("so far"));

        assertThat(textOf("progress-decisions"))
                .contains("3 of 4 samples agree")
                .contains("so far");
        assertThat(button("progress-apply").isDisabled()).isTrue();
        assistant.releaseAnswer();
        awaitApplicable();
    }

    // A failed suggestion offers no Apply: the dialog ends as any other and the card says what went wrong.
    @Test
    void failure_modelCannotAnswer_closesTheDialogWritesNothingAndSaysWhy() throws Exception {
        openAndShowBrief();
        assistant.answersBrief(Result.err(AppError.of(ErrorCode.validation, "Unreadable", "It could not be read.")));
        final int saves = projects.briefs().size();

        onFx(() -> button("brief-suggest-style").fire());
        awaitFx(() -> !textOf("brief-suggest-outcome").isEmpty());

        assertThat(optional("progress-apply") == null
                        || !optional("progress-apply").isVisible())
                .isTrue();
        assertThat(textOf("brief-suggest-outcome")).isEqualTo("It could not be read.");
        assertThat(projects.briefs()).hasSize(saves);
    }

    private List<JobEvent> sampleCall() {
        final List<JobEvent> events = new ArrayList<>(BriefFillFixtures.firstSampleStarted());
        events.add(new CallSnapshotUpdated(CallSnapshot.waiting(
                1,
                CallKind.BRIEF_SUGGESTION,
                "sample-1",
                null,
                List.of(),
                List.of(),
                Instant.parse("2026-10-22T10:00:00Z"),
                1,
                1,
                Duration.ofMinutes(1))));
        return events;
    }

    // IF Detailed were the only view, THEN the plain person would be shown call numbers they did not ask for.
    @Test
    void call_simpleDialog_hidesTheCallLine() throws Exception {
        openAndShowBrief();
        assistant.announces(sampleCall());
        assistant.holdAnswer();

        fillIn();

        assertThat(required("progress-call").isVisible()).isFalse();
        assistant.releaseAnswer();
    }

    @Test
    void call_detailedDialog_showsTheCallLine() throws Exception {
        openAndShowBrief();
        onFx(() -> injector.getInstance(ProgressDialogDefaults.class)
                .initialMode()
                .set(ProgressDialogModel.Mode.DETAILED));
        assistant.announces(sampleCall());
        assistant.holdAnswer();

        fillIn();
        awaitFx(() -> required("progress-call").isVisible());

        assertThat(required("progress-call").isVisible()).isTrue();
        assistant.releaseAnswer();
    }
}
