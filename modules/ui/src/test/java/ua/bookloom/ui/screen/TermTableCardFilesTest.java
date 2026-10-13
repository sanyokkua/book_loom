package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.GlossaryImportReport;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.ui.RecordingCsvChooser;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.state.CsvFileChooser;

/** Search, CSV import and export and the hover explanations of the two term cards (15i.U5). */
class TermTableCardFilesTest extends TermTableCardTestBase {

    // ---- recurring terms gain search and CSV --------------------------------------------------------------------

    private void type(final String id, final String text) {
        onFx(() -> ((TextField) required(id)).setText(text));
    }

    private List<String> listedTerms() {
        return ThemeTestSupport.onFx(() -> table("recurring").getItems().stream()
                .map(item -> ((LexiconEntry) item).term())
                .toList());
    }

    private static List<String> termsOf(final String commaSeparated) {
        return commaSeparated.isEmpty() ? List.of() : List.of(commaSeparated.split(","));
    }

    // IF the Recurring terms card had no search, THEN a long list would be read row by row.
    @ParameterizedTest
    @CsvSource(value = {"mas, master", "IMP, imp", "господар, master", "ZZZ, ''"})
    void search_recurringTerms_keepsTheRowsWhoseTermOrRenderingMatches(final String typed, final String kept)
            throws Exception {
        lexicon.holds(MASTER, IMP);
        showScreen("en", 1024, 700, ROSALIND);
        awaitFx(() -> table("recurring").getItems().size() == 2);

        type("recurring-search", typed);

        assertThat(listedTerms()).containsExactlyElementsOf(termsOf(kept));
    }

    @Test
    void search_recurringTerms_escapeClearsItAndShowsEveryRowAgain() throws Exception {
        lexicon.holds(MASTER, IMP);
        showScreen("en", 1024, 700, ROSALIND);
        awaitFx(() -> table("recurring").getItems().size() == 2);
        type("recurring-search", "mas");

        onFx(() -> required("recurring-search")
                .fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false)));

        assertThat(((TextField) required("recurring-search")).getText()).isEmpty();
        assertThat(listedTerms()).containsExactlyInAnyOrder("master", "imp");
    }

    private RecordingCsvChooser chooser() {
        return (RecordingCsvChooser) injector.getInstance(CsvFileChooser.class);
    }

    private void queueImportAnswers() {
        glossary.willAnswer(Result.ok(new GlossaryImportReport(0, List.of(), List.of())));
        glossary.willAnswer(Result.ok(List.of()));
    }

    /** Presses a card's Import: the button while the card is wide, the menu item while it is folded. */
    private void pressImport(final String card, final boolean folded) {
        onFx(() -> {
            if (folded) {
                ((MenuButton) required(card + "-more")).getItems().get(0).fire();
            } else {
                button(card + "-import").fire();
            }
        });
    }

    private void pressExport(final String card, final boolean folded) {
        onFx(() -> {
            if (folded) {
                ((MenuButton) required(card + "-more")).getItems().get(1).fire();
            } else {
                button(card + "-export").fire();
            }
        });
    }

    private boolean called(final String card, final String what) {
        final List<String> calls = "recurring".equals(card) ? lexicon.calls() : glossary.calls();
        return calls.stream().anyMatch(call -> call.startsWith(what + "(") && call.endsWith("csv)"));
    }

    // IF the Recurring terms card's Import did not reach its service, THEN the button would be decoration; and the
    // Glossary's must still reach the glossary's, through the same chooser seam.
    @ParameterizedTest
    @CsvSource({
        "names-style, 1300, 800, false",
        "recurring, 1300, 800, false",
        "names-style, 960, 640, true",
        "recurring, 960, 640, true"
    })
    void import_fileChosen_readsItThroughTheCardsOwnService(
            final String card, final double width, final double height, final boolean folded) throws Exception {
        showScreen("en", width, height, ROSALIND);
        queueImportAnswers();
        lexicon.importWillAdd(LexiconEntry.of("p1", "pentacle"));
        chooser().answer(CHOSEN);

        pressImport(card, folded);

        awaitFx(() -> called(card, "importCsv"));
        assertThat(chooser().asked()).containsExactly("open");
        assertThat(called("recurring".equals(card) ? "names-style" : "recurring", "importCsv"))
                .isFalse();
    }

    @Test
    void import_recurringTerms_showsTheImportedTermInTheTable() throws Exception {
        showScreen("en", 1300, 800, ROSALIND);
        lexicon.importWillAdd(LexiconEntry.of("p1", "pentacle"));
        chooser().answer(CHOSEN);

        pressImport("recurring", false);

        awaitFx(() -> listedTerms().contains("pentacle"));
    }

    @ParameterizedTest
    @CsvSource({
        "names-style, 1300, 800, false, glossary.csv",
        "recurring, 1300, 800, false, recurring-terms.csv",
        "names-style, 960, 640, true, glossary.csv",
        "recurring, 960, 640, true, recurring-terms.csv"
    })
    void export_fileChosen_writesItThroughTheCardsOwnServiceStartingFromItsOwnName(
            final String card, final double width, final double height, final boolean folded, final String name)
            throws Exception {
        showScreen("en", width, height, ROSALIND);
        glossary.willAnswer(Result.ok(Path.of("out.csv")));
        chooser().answer(Path.of("out.csv"));

        pressExport(card, folded);

        awaitFx(() -> called(card, "exportCsv"));
        assertThat(chooser().asked()).containsExactly("save:" + name);
    }

    // IF a cancelled dialog still called the service, THEN a stray click would overwrite or re-import a file.
    @ParameterizedTest
    @CsvSource({"names-style", "recurring"})
    void importAndExport_dialogCancelled_callsNoService(final String card) throws Exception {
        showScreen("en", 1300, 800, ROSALIND);
        chooser().answer(null);

        pressImport(card, false);
        pressExport(card, false);

        assertThat(chooser().asked())
                .containsExactly("open", "save:" + ("recurring".equals(card) ? "recurring-terms.csv" : "glossary.csv"));
        assertThat(called(card, "importCsv")).isFalse();
        assertThat(called(card, "exportCsv")).isFalse();
    }

    // ---- every control explains itself ---------------------------------------------------------------------------

    // IF a control in either card had no hover text, THEN the person would have to guess what it does.
    @ParameterizedTest
    @CsvSource({"en, 960, 640", "en, 1300, 800", "uk, 960, 640", "uk, 1300, 800"})
    void cards_everyOperableControl_hasAHoverExplanation(final String tag, final double width, final double height)
            throws Exception {
        lexicon.holds(MASTER);
        showScreen(tag, width, height, ROSALIND, HALE);
        awaitFx(() -> !table("recurring").getItems().isEmpty());

        assertThat(ThemeTestSupport.onFx(() -> TooltipProbe.untipped(required("names-style-card"))))
                .isEmpty();
        assertThat(ThemeTestSupport.onFx(() -> TooltipProbe.untipped(required("recurring-card"))))
                .isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"names-style", "recurring"})
    void menu_foldedToolbar_explainsItself(final String card) throws Exception {
        showScreen("en", 960, 640, ROSALIND);

        assertThat(TooltipProbe.tipText(required(card + "-more"))).isNotBlank();
        assertThat(TooltipProbe.tipText(required(card + "-search"))).isNotBlank();
    }
}
