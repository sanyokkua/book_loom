package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.ViewNames;

/**
 * The Used column of the Recurring terms card: how often the book writes each word, read from the lexicon service off
 * the FX thread, worded with plural forms, sortable, and never a zero for a count that has not arrived.
 */
class RecurringTermsUsedTest extends TranslatingScreenTestBase {

    private static final LexiconEntry WARDEN = LexiconEntry.of("p1", "warden");
    private static final LexiconEntry LANTERN = LexiconEntry.of("p1", "lantern");
    private static final LexiconEntry WICK = LexiconEntry.of("p1", "wick");
    private static final LexiconEntry OIL = LexiconEntry.of("p1", "oil");

    private void showCard(final String tag, final LexiconEntry... held) throws Exception {
        useLocale(Locale.forLanguageTag(tag));
        readyToStart();
        lexicon.holds(held);
        glossary.willAnswer(Result.ok(List.of()));
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
        awaitFx(() -> !table().getItems().isEmpty());
    }

    private TableView<?> table() {
        return (TableView<?>) required("recurring-table");
    }

    private List<String> usedTexts() {
        return ThemeTestSupport.onFx(() -> table().lookupAll(".recurring-used").stream()
                .filter(node -> node.getScene() != null)
                .map(node -> ((Label) node).getText())
                .toList());
    }

    private List<String> terms() {
        return ThemeTestSupport.onFx(() -> table().getItems().stream()
                .map(item -> ((LexiconEntry) item).term())
                .toList());
    }

    // IF the count were worded by hand, THEN Ukrainian would read "22 разів" where the language says "22 рази".
    @ParameterizedTest
    @CsvSource({
        "en, 13, 13 times",
        "en, 1, 1 time",
        "uk, 13, 13 разів",
        "uk, 1, 1 раз",
        "uk, 22, 22 рази",
        "uk, 5, 5 разів"
    })
    void used_countLoaded_isWordedWithThePluralFormOfItsLanguage(
            final String tag, final int count, final String expected) throws Exception {
        lexicon.usageIs(Map.of("warden", count));

        showCard(tag, WARDEN);

        awaitFx(() -> usedTexts().contains(expected));
        assertThat(usedTexts()).containsExactly(expected);
    }

    // IF a count were shown before it arrived, THEN every row would read "0 times" for a moment and a person would
    // believe the word never occurs.
    @Test
    void used_notLoadedYet_showsNothingAndNeverZero() throws Exception {
        lexicon.usageIs(Map.of());

        showCard("en", WARDEN);

        assertThat(usedTexts()).isNotEmpty().allMatch(String::isEmpty);
    }

    // IF the column sorted by the shown text, THEN "10 times" would sort before "9 times".
    @Test
    void used_headerSortedAscending_ordersByCountAndKeepsTheTermOrderOnATie() throws Exception {
        lexicon.usageIs(Map.of("lantern", 10, "wick", 9, "oil", 10));
        showCard("en", LANTERN, WICK, OIL);
        awaitFx(() -> usedTexts().contains("10 times"));
        final TableColumn<?, ?> used =
                ThemeTestSupport.onFx(() -> table().getColumns().get(2));

        onFx(() -> sortAscending(used));

        assertThat(terms()).containsExactly("wick", "lantern", "oil");
    }

    // IF the order were fixed when the person clicked the header, THEN counts that load later would leave the rows in
    // the order of the empty column, and the sort would look broken.
    @Test
    void used_countsArriveAfterTheSort_rowsAreOrderedAgainByTheNewCounts() throws Exception {
        lexicon.usageIs(Map.of());
        showCard("en", LANTERN, WICK, OIL);
        final TableColumn<?, ?> used =
                ThemeTestSupport.onFx(() -> table().getColumns().get(2));
        onFx(() -> sortAscending(used));
        assertThat(terms()).containsExactly("lantern", "oil", "wick");
        lexicon.usageIs(Map.of("lantern", 10, "wick", 9, "oil", 10));

        onFx(() -> button("recurring-add").fire());
        awaitFx(() -> optional("recurring-add-card") != null);
        onFx(() -> ((TextField) required("recurring-add-term")).setText("pearl"));
        onFx(() -> button("recurring-add-confirm").fire());

        awaitFx(() -> usedTexts().contains("9 times"));
        assertThat(terms()).containsExactly("wick", "lantern", "oil", "pearl");
    }

    // What a click on the header does, without the pointer.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void sortAscending(final TableColumn<?, ?> column) {
        column.setSortType(TableColumn.SortType.ASCENDING);
        ((TableView) table()).getSortOrder().setAll(column);
    }

    // IF a click on Used did not explain it, THEN a person would not know a different form is not counted.
    @Test
    void used_header_explainsWhatIsCounted() throws Exception {
        showCard("en", WARDEN);

        final Label header =
                ThemeTestSupport.onFx(() -> (Label) table().getColumns().get(2).getGraphic());

        assertThat(ThemeTestSupport.onFx(() -> TooltipProbe.tipText(header)))
                .startsWith("How often the book writes this word, any letter case.");
    }

    // IF the counts were read on the FX thread, THEN a large book would freeze the window on every added term.
    @Test
    void used_afterAdd_reloadsOffTheFxThread() throws Exception {
        lexicon.usageIs(Map.of("warden", 13, "wick", 2));
        showCard("en", WARDEN);
        final int before = lexicon.usageOnFx().size();

        onFx(() -> button("recurring-add").fire());
        awaitFx(() -> optional("recurring-add-card") != null);
        onFx(() -> ((TextField) required("recurring-add-term")).setText("wick"));
        onFx(() -> button("recurring-add-confirm").fire());

        awaitFx(() -> usedTexts().contains("2 times"));
        assertThat(lexicon.usageOnFx().size()).isGreaterThan(before);
        assertThat(lexicon.usageOnFx()).allMatch(onFx -> !onFx);
    }
}
