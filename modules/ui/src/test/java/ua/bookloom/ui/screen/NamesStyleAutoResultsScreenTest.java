package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.AutoNamesFixture.HALE;
import static ua.bookloom.ui.AutoNamesFixture.WELL;

import java.util.List;
import javafx.scene.control.ToggleButton;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
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
}
