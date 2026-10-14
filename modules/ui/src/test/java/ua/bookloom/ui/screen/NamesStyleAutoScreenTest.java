package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.AutoNamesFixture.PENTACLE;

import java.util.List;
import java.util.Locale;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.NamesEstimate;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.state.BookBriefViewModel;

/**
 * "Prepare names automatically" on Names &amp; style (15i.U6): the button above both cards and the estimate under it,
 * the run on the shared busy card with its Cancel, the other model actions waiting, and a stop or a failure that
 * changes nothing. The port is a recording fake; the window and the busy card are real.
 */
class NamesStyleAutoScreenTest extends NamesStyleAutoScreenTestBase {

    // IF the button sat inside a card or below them, THEN it would not read as one step for both lists.
    @Test
    void button_screenShown_sitsAboveBothCardsAndIsCaptionedWithTheAction() throws Exception {
        showScreen();

        final Button auto = button(AUTO);
        final double bottom = ThemeTestSupport.onFx(() -> sceneBoundsOf(auto).getMaxY());
        final double glossaryTop = ThemeTestSupport.onFx(
                () -> sceneBoundsOf(required("names-style-card")).getMinY());
        final double recurringTop = ThemeTestSupport.onFx(
                () -> sceneBoundsOf(required("recurring-card")).getMinY());
        assertThat(auto.getText()).isEqualTo("Prepare names automatically");
        assertThat(bottom).isLessThanOrEqualTo(glossaryTop);
        assertThat(bottom).isLessThanOrEqualTo(recurringTop);
    }

    private static Bounds sceneBoundsOf(final Node node) {
        return node.localToScene(node.getBoundsInLocal());
    }

    // IF the tip did not name both lists, THEN hovering would not tell the person what one press touches.
    @Test
    void button_hovered_explainsThatItPreparesTheNamesAndTheRecurringTerms() throws Exception {
        showScreen();

        assertThat(TooltipProbe.tipText(required(AUTO)))
                .contains("names")
                .contains("recurring terms")
                .contains("Nothing is changed");
    }

    // IF the person had to guess how long one press takes, THEN a twenty-minute run would start as a surprise.
    @Test
    void subtitle_estimateAnswered_saysTheMinutesAndTheDialOfTheBrief() throws Exception {
        namesPass.willEstimate(new NamesEstimate(12, 15, 25));

        showScreen();

        assertThat(labelText(SUBTITLE)).isEqualTo("about 15–25 min on Balanced");
        assertThat(namesPass.estimateCalls()).contains("p1");
    }

    // IF the line were composed once, THEN moving the dial on the brief would leave the old minutes on this button.
    @Test
    void subtitle_dialChangedToMax_asksForANewEstimateAndNamesTheNewDial() throws Exception {
        namesPass.willEstimate(new NamesEstimate(12, 15, 25));
        showScreen();
        final int askedBefore = namesPass.estimateCalls().size();
        namesPass.willEstimate(new NamesEstimate(40, 30, 60));

        onFx(() -> injector.getInstance(BookBriefViewModel.class).setDial(QualityDial.MAX));

        awaitFx(() -> "about 30–60 min on Max quality".equals(labelText(SUBTITLE)));
        assertThat(namesPass.estimateCalls().size()).isGreaterThan(askedBefore);
    }

    // IF a failed estimate printed a made-up time, THEN the line would promise minutes nobody computed.
    @Test
    void subtitle_portCannotEstimate_isBlankAndTheButtonStaysAvailable() throws Exception {
        namesPass.willFailToEstimate(AppError.of(ErrorCode.validation, "No book", "The book is not open."));

        showScreen();

        assertThat(labelText(SUBTITLE)).isBlank();
        assertThat(button(AUTO).isDisabled()).isFalse();
    }

    // IF the words came from the English bundle only, THEN a Ukrainian window would mix languages on its main button.
    @Test
    void button_ukrainianInterface_isWordedInUkrainianWithTheUkrainianDialLabel() throws Exception {
        useLocale(Locale.forLanguageTag("uk"));
        namesPass.willEstimate(new NamesEstimate(12, 15, 25));

        showScreen();

        assertThat(button(AUTO).getText()).isEqualTo("Підготувати імена автоматично");
        assertThat(labelText(SUBTITLE)).startsWith("приблизно 15–25 хв").contains("Збалансовано");
        assertThat(TooltipProbe.tipText(required(AUTO))).isNotBlank().contains("Повторювані терміни");
    }

    // IF the button did not reach the port, THEN it would be decoration.
    @Test
    void press_modelChosen_runsThePassOnceForTheOpenProject() throws Exception {
        showScreen();

        runToTheResults();

        assertThat(namesPass.runs()).hasSize(1);
        assertThat(namesPass.runs().getFirst().projectId()).isEqualTo("p1");
        assertThat(models.selections()).hasSize(1);
    }

    // IF the pass ran with no card, THEN the window would look frozen for twenty minutes.
    @Test
    void press_running_showsTheBusyCardNamedForThePassWithACancelThatIsOn() throws Exception {
        showScreen();
        namesPass.hold();

        pressAuto();

        assertThat(namesPass.awaitStarted()).isTrue();
        awaitFx(this::isBusyCardShown);
        assertThat(labelText("busy-title")).isEqualTo("Preparing names");
        assertThat(button("busy-cancel").isDisabled()).isFalse();
    }

    // IF a second model action could start beside the pass, THEN two requests would queue in the one inference gate.
    @Test
    void press_running_disablesTheButtonAndEveryOtherModelAction() throws Exception {
        showScreen();
        namesPass.hold();

        pressAuto();
        assertThat(namesPass.awaitStarted()).isTrue();
        awaitFx(() -> button("names-style-scan").isDisabled());

        assertThat(List.of(
                        AUTO,
                        "names-style-scan",
                        "names-style-review",
                        "names-style-translate",
                        "recurring-scan",
                        "recurring-review",
                        "recurring-translate"))
                .allSatisfy(id -> assertThat(button(id).isDisabled()).as(id).isTrue());
    }

    // IF the pass could start while a scan runs, THEN the scan's results and the pass's would collide.
    @Test
    void button_anotherModelActionRuns_isDisabled() throws Exception {
        showScreen();
        glossary.holdModelCalls();

        onFx(() -> button("names-style-review").fire());
        awaitFx(() -> isShown("names-style-stop"));

        assertThat(button(AUTO).isDisabled()).isTrue();
    }

    // IF Cancel only closed the card, THEN the pass would go on writing behind a window that says it stopped.
    @Test
    void cancel_pressedOnTheBusyCard_interruptsThePassAndBothTablesAreUnchanged() throws Exception {
        showScreen();
        passWillSucceed();
        namesPass.hold();
        pressAuto();
        assertThat(namesPass.awaitStarted()).isTrue();
        awaitFx(this::isBusyCardShown);

        onFx(() -> button("busy-cancel").fire());

        assertThat(namesPass.awaitCancelled()).isTrue();
        awaitFx(() -> isCardShown("results-card"));
        assertThat(textsUnder(required("results-card"))).contains("Stopped. Nothing was changed.");
        assertThat(glossaryTerms()).containsExactly("Well", "Hale");
        assertThat(recurringTerms()).containsExactly("table");
        assertThat(glossary.added()).isEmpty();
        assertThat(glossary.updated()).isEmpty();
        assertThat(lexicon.calls()).noneMatch(call -> call.startsWith("remove(") || call.startsWith("restore("));
        assertThat(isBusyCardShown()).isFalse();
    }

    // IF a failed pass left a half-written table, THEN "changes nothing" would be untrue; the error is shown in place.
    @Test
    void press_passFails_showsTheErrorInPlaceOpensNoResultsAndChangesNothing() throws Exception {
        showScreen();
        namesPass.willAnswer(Result.err(AppError.of(ErrorCode.timeout, "Timed out", "The model did not answer.")));
        namesPass.writes(() -> lexicon.replaceHeld(PENTACLE));

        pressAuto();

        awaitFx(() -> "The model did not answer.".equals(labelText("names-style-notice-text")));
        assertThat(isCardShown("results-card")).isFalse();
        assertThat(glossaryTerms()).containsExactly("Well", "Hale");
        assertThat(recurringTerms()).containsExactly("table");
        assertThat(button(AUTO).isDisabled()).isFalse();
    }

    // IF a person without a model started a pass, THEN it would fail on a model that does not exist.
    @Test
    void press_noModelChosen_runsNothingAndShowsTheErrorInPlace() throws Exception {
        showScreenWithoutModel();

        pressAuto();

        awaitFx(() -> isShown("names-style-notice"));
        assertThat(namesPass.runs()).isEmpty();
        assertThat(isCardShown("results-card")).isFalse();
    }
}
