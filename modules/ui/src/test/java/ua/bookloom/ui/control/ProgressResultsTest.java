package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.ChangeKind;
import ua.bookloom.ui.state.ChangeOperation;
import ua.bookloom.ui.state.ChangeOrigin;
import ua.bookloom.ui.state.ChangeResults;
import ua.bookloom.ui.state.ChangeRow;
import ua.bookloom.ui.state.ChangeSection;
import ua.bookloom.ui.state.LiveCalls;

/**
 * The Results view of the progress dialog: the header with its counts, steps and time, the Changes tab with its rows
 * and columns, the Steps tab with each stage's requests, and the lines for a pass that changed nothing or was stopped.
 */
class ProgressResultsTest extends ProgressResultsTestBase {

    // IF the header gave counts only, THEN the person could not tell how large and how long the operation was.
    @Test
    void subtitle_threeChanges_readsFiveAddedZeroRemovedOneChangedFortyNineStepsOneMinEightSeconds() {
        show(timed(
                49,
                108,
                added(0, "Mid"),
                added(1, "Hale"),
                added(2, "Fen"),
                added(3, "Oak"),
                added(4, "Elm"),
                changed(5, "Well")));

        assertThat(((Label) required("progress-title")).getText()).isEqualTo("Name review — results");
        assertThat(((Label) required("progress-subtitle")).getText())
                .isEqualTo("5 added · 0 removed · 1 changed · 49 steps · 1 min 48 s");
    }

    private static ChangeRow changed(final long id, final String term) {
        return new ChangeRow(
                id,
                ChangeKind.CHANGED,
                term,
                "No target · other",
                "No target · character",
                null,
                ChangeOrigin.MODEL,
                ChangeSection.NAMES);
    }

    // IF a change had no badge, THEN the Changes tab could not be scanned for what was added and what was dropped.
    @Test
    void changes_twoAddedOneRemoved_listsThreeRowsWithAChangeBadgeEach() {
        show(timed(3, 20, found(0, "Mira Holt"), found(1, "lantern"), removed(2, "Hale", "not a name")));

        assertThat(((Label) required("progress-subtitle")).getText()).startsWith("2 added · 1 removed · 0 changed");
        assertThat(texts()).contains("Change", "Mira Holt", "lantern", "Hale");
        assertThat(texts().stream().map(String::toLowerCase).toList()).contains("added", "removed");
    }

    // IF the Steps tab were missing, THEN a person could not see where the minutes of the pass went.
    @Test
    void tabs_opened_areChangesThenSteps() {
        show(ofPass(new int[] {0, 6, 0, 3, 25, 4, 2, 0}, added(0, "Mid")));

        final TabPane tabs = (TabPane) required("results-tabs");

        assertThat(ThemeTestSupport.onFx(
                        () -> tabs.getTabs().stream().map(Tab::getText).toList()))
                .containsExactly("Changes", "Steps");
        assertThat(ThemeTestSupport.onFx(() -> tabs.getSelectionModel().getSelectedIndex()))
                .isZero();
    }

    // IF the Steps tab listed requests, THEN a pass of forty calls would bury its eight stages.
    @Test
    void tabs_stepsSelected_listsTheEightStepsWithTheirCounts() {
        show(ofPass(new int[] {0, 6, 0, 3, 25, 4, 2, 0}, added(0, "Mid")));

        selectTab(1);

        final ListView<?> steps = (ListView<?>) required("results-steps");
        assertThat(ThemeTestSupport.onFx(() -> steps.getItems().size())).isEqualTo(8);
        assertThat(texts())
                .contains(
                        "Find candidates in the book",
                        "Read repeated words in context",
                        "Write the results",
                        "25 requests",
                        "6 requests",
                        "no request");
    }

    // IF a name the pass could not give a target stayed unlisted, THEN the person would find it only by scanning the
    // table.
    @ParameterizedTest(name = "noRows={0}")
    @ValueSource(booleans = {false, true})
    void show_namesWithoutATarget_areListedForThePersonWhateverTheOutcome(final boolean noRows) {
        final ChangeResults results = new ChangeResults(
                ChangeOperation.NAME_REVIEW,
                false,
                noRows ? List.of() : items(added(0, "Mid")),
                LiveCalls.EMPTY,
                List.of("Hale", "Well"));

        show(results);

        assertThat(texts()).contains("Still without a target (2): Hale, Well. Give each one in the glossary.");
    }

    @Test
    void show_everyNameHasATarget_listsNoUntargetedLine() {
        show(results(false, added(0, "Mid")));

        assertThat(texts()).noneMatch(text -> text.startsWith("Still without a target"));
    }

    // IF text finds and model additions shared one "Added" heading, THEN the person could not tell what needed no
    // model.
    @Test
    void show_addedRowsOfBothOrigins_groupsThemUnderFoundInTheTextAndAddedByTheModel() {
        show(results(false, found(0, "imp"), added(1, "pentacle"), found(2, "master")));

        assertThat(texts()).contains("Found in the text (2)", "Added by the model (1)");
        assertThat(texts().indexOf("Found in the text (2)")).isLessThan(texts().indexOf("Added by the model (1)"));
    }

    @Test
    void show_addedRowsOfOneOrigin_keepsThePlainAddedHeading() {
        show(results(false, added(0, "Mid"), added(1, "Hale")));

        assertThat(texts()).contains("Added (2)").doesNotContain("Added by the model (2)", "Found in the text (0)");
    }

    @Test
    void show_aRowWithAReason_showsTheWhyColumnWithTheReasonAndADashElsewhere() {
        show(results(false, added(0, "Mid"), removed(1, "Well", "not a name")));

        assertThat(texts()).contains("Why", "not a name", "—");
    }

    // IF the column showed with nothing in it, THEN every row would carry a dead cell.
    @Test
    void show_noRowHasAReason_hasNoWhyColumn() {
        show(results(false, added(0, "Mid")));

        assertThat(texts()).doesNotContain("Why", "not a name");
    }

    @Test
    void show_nothingChanged_saysSoInOnePlainLineWithNoRowsOrUndo() {
        show(results(false));

        assertThat(texts()).contains("Nothing changed.");
        assertThat(scene.getRoot().lookup("#results-undo-all")).isNull();
        assertThat(scene.getRoot().lookup("#results-list")).isNull();
    }

    @Test
    void show_stopped_saysNothingWasChanged() {
        show(results(true));

        assertThat(texts()).contains("Stopped. Nothing was changed.");
    }

    @Test
    void list_manyRows_hasFixedRowHeightsAndOneItemPerRowAndHeading() {
        show(results(false, added(0, "Mid"), removed(1, "Well", null)));

        final ListView<?> list = (ListView<?>) required("results-list");

        assertThat(list.getFixedCellSize()).isEqualTo(40.0);
        assertThat(ThemeTestSupport.onFx(() -> list.getItems().size())).isEqualTo(4);
    }
}
