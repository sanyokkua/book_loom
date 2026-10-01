package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.event.Event;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.GlossaryReviewReport;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.NamesStyleViewModel;
import ua.bookloom.ui.state.SettingsViewModel;

/** The glossary table sorts by a header, filters by the search field, and the review button reaches the service. */
class GlossaryTableSortSearchTest extends TranslatingScreenTestBase {

    private static final GlossaryEntry ZETA =
            new GlossaryEntry("e1", "p1", "Zeta", null, TermType.PLACE, Gender.UNKNOWN, false);
    private static final GlossaryEntry ALPHA =
            new GlossaryEntry("e2", "p1", "Alpha", null, TermType.CHARACTER, Gender.FEMALE, true);
    private static final GlossaryEntry MID =
            new GlossaryEntry("e3", "p1", "Mid", "Мід", TermType.OTHER, Gender.UNKNOWN, false);
    private static final GlossaryEntry BETA =
            new GlossaryEntry("e4", "p1", "Beta", null, TermType.OTHER, Gender.UNKNOWN, false);

    private void showGlossary() throws Exception {
        projects.on(BOOK, Result.ok(BookFixtures.frankensteinImport()));
        openImport();
        openBook(BOOK);
        chooseTarget();
        glossary.willAnswer(Result.ok(List.of(ZETA, ALPHA, MID, BETA)));
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
    }

    @SuppressWarnings("unchecked")
    private TableView<GlossaryEntry> table() {
        return (TableView<GlossaryEntry>) required("names-style-table");
    }

    private List<String> shownTerms() {
        return ThemeTestSupport.onFx(
                () -> table().getItems().stream().map(GlossaryEntry::term).toList());
    }

    private void sortBy(final int column) {
        onFx(() -> table().getSortOrder().setAll(List.of(table().getColumns().get(column))));
    }

    // IF the table could not be sorted, THEN finding one name among hundreds would mean scrolling through them all.
    @Test
    void sortByTerm_headerChosen_ordersTheRowsByTerm() throws Exception {
        showGlossary();

        sortBy(0);

        assertThat(shownTerms()).containsExactly("Alpha", "Beta", "Mid", "Zeta");
    }

    // IF rows of one type were left in any order, THEN a sort by type would shuffle them on every edit.
    @Test
    void sortByType_rowsOfOneType_areOrderedByTerm() throws Exception {
        showGlossary();

        sortBy(1);

        assertThat(shownTerms()).containsExactly("Alpha", "Zeta", "Beta", "Mid");
    }

    // IF a stored target moved its row while the table is sorted by another column, THEN the row would jump away.
    @Test
    void setTarget_tableSortedByType_keepsTheRowInPlace() throws Exception {
        showGlossary();
        sortBy(1);
        glossary.willAnswer(
                Result.ok(new GlossaryEntry("e4", "p1", "Beta", "Бета", TermType.OTHER, Gender.UNKNOWN, false)));

        onFx(() -> injector.getInstance(NamesStyleViewModel.class).setTarget("e4", "Бета"));
        awaitFx(() -> "Бета".equals(table().getItems().get(2).target()));

        assertThat(shownTerms()).containsExactly("Alpha", "Zeta", "Beta", "Mid");
    }

    // IF the search did not filter by target too, THEN a name known only by its translation could not be found.
    @Test
    void search_typedTargetText_showsOnlyMatchingRowsAndEscapeShowsAllAgain() throws Exception {
        showGlossary();
        final TextField search = (TextField) required("names-style-search");

        onFx(() -> search.setText("мід"));
        assertThat(shownTerms()).containsExactly("Mid");

        onFx(() -> Event.fireEvent(
                search, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false)));
        assertThat(ThemeTestSupport.onFx(() -> search.getText())).isEmpty();
        assertThat(shownTerms()).hasSize(4);
    }

    // IF the review button did not reach the service, THEN the model could never clean the list.
    @Test
    void reviewButton_modelChosen_asksTheServiceAndShowsTheRowsItLeft() throws Exception {
        showGlossary();
        onFx(() -> injector.getInstance(SettingsViewModel.class).model().set(MODEL));
        final GlossaryEntry typed = new GlossaryEntry("e3", "p1", "Mid", "Мід", TermType.CHARACTER, Gender.MALE, false);
        glossary.willAnswer(Result.ok(new GlossaryReviewReport(3, 1, List.of(typed))));

        onFx(() -> button("names-style-review").fire());
        awaitFx(() -> table().getItems().size() == 1);

        assertThat(glossary.calls()).contains("review(p1)");
        assertThat(labelText("names-style-notice-text")).isEqualTo("Model review done: 3 rows removed, 1 row updated.");
    }
}
