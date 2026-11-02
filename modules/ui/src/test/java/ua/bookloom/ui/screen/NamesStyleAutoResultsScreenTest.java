package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.AutoNamesFixture.HALE;
import static ua.bookloom.ui.AutoNamesFixture.MID;
import static ua.bookloom.ui.AutoNamesFixture.PENTACLE;
import static ua.bookloom.ui.AutoNamesFixture.WELL;
import static ua.bookloom.ui.AutoNamesFixture.WELL_TYPED;

import java.util.List;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.NamesPassReport;
import ua.bookloom.ui.AutoNamesFixture;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * The results card of "Prepare names automatically" (15i.U6): one card with a Names and a Recurring terms section, each
 * row reverted alone, Undo all across both tables and "Show changed rows" on both cards.
 */
class NamesStyleAutoResultsScreenTest extends NamesStyleAutoScreenTestBase {

    // IF the results were two separate cards, THEN Undo all could not put both tables back in one press.
    @Test
    void results_passSucceeds_opensOneCardWithANamesAndARecurringTermsSection() throws Exception {
        showScreen();

        runToTheResults();

        final List<String> texts = textsUnder(required("results-card"));
        assertThat(texts)
                .contains(
                        "Prepare names automatically — results",
                        "Names (3)",
                        "Recurring terms (2)",
                        "Mid",
                        "Hale",
                        "Well",
                        "pentacle",
                        "table");
        assertThat(labelText("progress-subtitle")).startsWith("2 added · 2 removed · 1 changed");
        assertThat(texts.indexOf("Names (3)")).isLessThan(texts.indexOf("Mid"));
        assertThat(texts.indexOf("Mid")).isLessThan(texts.indexOf("Recurring terms (2)"));
        assertThat(texts.indexOf("Recurring terms (2)")).isLessThan(texts.indexOf("pentacle"));
    }

    @Test
    void results_passSucceeds_bothTablesShowTheResult() throws Exception {
        showScreen();

        runToTheResults();

        assertThat(glossaryTerms()).containsExactlyInAnyOrder("Well", "Mid");
        assertThat(recurringTerms()).containsExactly("pentacle");
    }

    // IF Revert on a name row did not reach the glossary, THEN the button would be decoration.
    @Test
    void revert_namesRowPressedInTheCard_putsThatNameBackOnlyInTheGlossary() throws Exception {
        showScreen();
        runToTheResults();
        glossary.willAnswer(Result.ok(true));

        onFx(() -> revertButtonFor("Mid").fire());

        awaitFx(() -> !glossaryTerms().contains("Mid"));
        assertThat(glossary.calls()).contains("remove(p1, e3)");
        assertThat(recurringTerms()).containsExactly("pentacle");
    }

    // IF Revert on a term row did not reach the lexicon, THEN the Recurring terms section could not be undone.
    @Test
    void revert_termRowPressedInTheCard_putsThatTermBackOnlyInTheRecurringTerms() throws Exception {
        showScreen();
        runToTheResults();

        onFx(() -> revertButtonFor("table").fire());

        awaitFx(() -> recurringTerms().contains("table"));
        assertThat(lexicon.calls()).contains("restore(table)");
        assertThat(glossaryTerms()).containsExactlyInAnyOrder("Well", "Mid");
    }

    // IF Undo all handled one section, THEN the pass would stay half applied.
    @Test
    void undoAll_pressedInTheCard_bothTablesAreAsTheyWereBeforeThePass() throws Exception {
        showScreen();
        runToTheResults();
        glossary.willAnswer(Result.ok(true));
        glossary.willAnswer(Result.ok(HALE));
        glossary.willAnswer(Result.ok(WELL));

        onFx(() -> button("results-undo-all").fire());

        awaitFx(() -> button("results-undo-all").isDisabled());
        assertThat(glossaryTerms()).containsExactlyInAnyOrder("Well", "Hale");
        assertThat(recurringTerms()).containsExactly("table");
    }

    // IF Show changed rows filtered one table only, THEN the other would keep every row.
    @Test
    void showChangedRows_pressedAfterThePass_filtersBothTablesToTheirChangedRows() throws Exception {
        showScreen();
        runToTheResults();

        onFx(() -> button("results-show-changed").fire());

        assertThat(ThemeTestSupport.onFx(() -> ((ToggleButton) required("names-style-changed-filter")).isSelected()))
                .isTrue();
        assertThat(ThemeTestSupport.onFx(() -> ((ToggleButton) required("recurring-changed-filter")).isSelected()))
                .isTrue();
        assertThat(glossaryTerms()).containsExactlyInAnyOrder("Well", "Mid");
        assertThat(recurringTerms()).containsExactly("pentacle");
    }

    // IF a target typed in the card were kept in the card, THEN the glossary would still lack it when the card closed.
    @Test
    void edit_targetTypedOnANameRow_isStoredThroughTheGlossaryEditTheTableUses() throws Exception {
        showScreen();
        runToTheResults();
        glossary.willAnswer(Result.ok(MID));

        typeInRow("Mid", "Мід");

        awaitFx(() -> glossary.calls().contains("update(Mid)"));
        assertThat(glossary.calls()).contains("update(Mid)");
    }

    // IF the term rows ignored the field, THEN a wrong rendering could only be fixed in the Recurring terms card.
    @Test
    void edit_renderingTypedOnATermRow_isStoredThroughTheLexiconEditTheCardUses() throws Exception {
        showScreen();
        runToTheResults();

        typeInRow("pentacle", "пентакль");

        awaitFx(() -> lexicon.calls().contains("edit(pentacle, пентакль)"));
        assertThat(lexicon.calls()).contains("edit(pentacle, пентакль)");
    }

    // IF the pass named its untargeted terms but the card listed none, THEN they would be found only by scanning the
    // table.
    @Test
    void results_passLeavesANameWithoutATarget_listsItAsARowToTypeIn() throws Exception {
        showScreen();
        final NamesPassReport base = AutoNamesFixture.report();
        namesPass.willAnswer(Result.ok(
                new NamesPassReport(base.names(), base.terms(), base.calls(), base.elapsed(), List.of(WELL.term()))));
        namesPass.writes(() -> lexicon.replaceHeld(PENTACLE));
        glossary.willAnswer(Result.ok(List.of(WELL_TYPED, MID)));
        pressAuto();
        awaitFx(() -> isCardShown("results-card"));

        final List<String> texts = textsUnder(required("results-card"));
        final long fields = ThemeTestSupport.onFx(() -> required("results-list").lookupAll("#results-target").stream()
                .filter(Node::isVisible)
                .count());

        assertThat(texts).contains("Still without a target (1)");
        assertThat(fields).isEqualTo(4);
    }

    private void typeInRow(final String term, final String text) {
        onFx(() -> {
            final TextField field = required("results-list").lookupAll(".list-cell").stream()
                    .filter(cell -> textsUnder(cell).contains(term))
                    .flatMap(cell -> cell.lookupAll("#results-target").stream())
                    .map(TextField.class::cast)
                    .findFirst()
                    .orElseThrow();
            field.setText(text);
            field.fireEvent(new ActionEvent());
        });
    }
}
