package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TreeItem;
import org.controlsfx.control.ToggleSwitch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ViewNames;

/**
 * The structure screen the application builds, read from the real scene: the flat, read-only list of the book's units
 * with each unit's path, position and segment count, the total beneath it, the bounded number of rows a huge book
 * materialises, and the state with no book open. Row texts are read from the rendered cells, never from the model.
 */
class StructureScreenTest extends StructureScreenTestBase {

    private static final List<String> ELEVEN_HREFS = List.of(
            "OEBPS/chapter-01.xhtml",
            "OEBPS/chapter-02.xhtml",
            "OEBPS/chapter-03.xhtml",
            "OEBPS/chapter-04.xhtml",
            "OEBPS/chapter-05.xhtml",
            "OEBPS/chapter-06.xhtml",
            "OEBPS/chapter-07.xhtml",
            "OEBPS/chapter-08.xhtml",
            "OEBPS/chapter-09.xhtml",
            "OEBPS/chapter-10.xhtml",
            "OEBPS/chapter-11.xhtml");

    private static final List<List<String>> ELEVEN_ROWS = List.of(
            List.of("OEBPS/chapter-01.xhtml", "Position 0", "120 segments"),
            List.of("OEBPS/chapter-02.xhtml", "Position 1", "95 segments"),
            List.of("OEBPS/chapter-03.xhtml", "Position 2", "130 segments"),
            List.of("OEBPS/chapter-04.xhtml", "Position 3", "110 segments"),
            List.of("OEBPS/chapter-05.xhtml", "Position 4", "100 segments"),
            List.of("OEBPS/chapter-06.xhtml", "Position 5", "115 segments"),
            List.of("OEBPS/chapter-07.xhtml", "Position 6", "105 segments"),
            List.of("OEBPS/chapter-08.xhtml", "Position 7", "125 segments"),
            List.of("OEBPS/chapter-09.xhtml", "Position 8", "135 segments"),
            List.of("OEBPS/chapter-10.xhtml", "Position 9", "90 segments"),
            List.of("OEBPS/chapter-11.xhtml", "Position 10", "115 segments"));

    private static final int UNITS_IN_A_HUGE_BOOK = 5_000;
    private static final int FAR_FEWER_THAN_ALL = 200;

    @TempDir
    private Path dir;

    /** Eleven units whose segment counts add up to 1,240. */
    private static Document elevenUnitBook() {
        return BookFixtures.book(
                "eleven", BookFormat.EPUB, ELEVEN_HREFS, 120, 95, 130, 110, 100, 115, 105, 125, 135, 90, 115);
    }

    // IF a row were dropped, reordered or given a neighbour's count, THEN the person would misread how the book parsed.
    @Test
    void tree_elevenUnitBook_rendersElevenRowsInOrderEachWithItsOwnCount() throws TimeoutException {
        openBookThenShowStructure(dir.resolve("book.epub"), elevenUnitBook());

        assertThat(renderedRows()).containsExactlyElementsOf(ELEVEN_ROWS);
    }

    // IF a unit were nested under another, THEN the screen would state a hierarchy the parsed model does not have.
    @Test
    void tree_elevenUnitBook_holdsElevenChildlessItemsUnderAnInvisibleRoot() throws TimeoutException {
        openBookThenShowStructure(dir.resolve("book.epub"), elevenUnitBook());

        assertThat(tree().isShowRoot()).isFalse();
        assertThat(tree().getRoot().getChildren()).hasSize(11);
        assertThat(tree().getRoot().getChildren())
                .allSatisfy(item -> assertThat(item.getChildren()).isEmpty())
                .allMatch(TreeItem::isLeaf);
    }

    // IF a title were shown, THEN the row would invent a chapter name the parse never produced; the row carries the
    // path, the position and the count and nothing else.
    @Test
    void row_unitAtPositionZero_showsItsPathItsPositionAndItsCountAndNoTitle() throws TimeoutException {
        final Document book = BookFixtures.book("one", BookFormat.EPUB, List.of("OEBPS/chapter-01.xhtml"), 1);
        openBookThenShowStructure(dir.resolve("book.epub"), book);

        assertThat(renderedRows()).hasSize(1);
        assertThat(renderedRows().get(0)).containsExactly("OEBPS/chapter-01.xhtml", "Position 0", "1 segment");
    }

    // IF the total were not the sum of the rows, THEN the screen would contradict itself.
    @Test
    void total_elevenUnitBook_isTheGroupedSumOfTheRowCounts() throws TimeoutException {
        openBookThenShowStructure(dir.resolve("book.epub"), elevenUnitBook());

        assertThat(renderedCountTexts())
                .containsExactly(
                        "120 segments",
                        "95 segments",
                        "130 segments",
                        "110 segments",
                        "100 segments",
                        "115 segments",
                        "105 segments",
                        "125 segments",
                        "135 segments",
                        "90 segments",
                        "115 segments");
        assertThat(((Label) required("structure-total")).getText()).isEqualTo("1,240 segments in total");
    }

    // IF a book with a single segment said "1 segments in total", THEN the plural rule would be wrong at its edge.
    @Test
    void total_singleSegmentBook_usesTheSingularForm() throws TimeoutException {
        final Document book = BookFixtures.book("one", BookFormat.TXT, List.of("book.txt"), 1);
        openBookThenShowStructure(dir.resolve("book.txt"), book);

        assertThat(((Label) required("structure-total")).getText()).isEqualTo("1 segment in total");
    }

    // IF every row of a huge book were materialised, THEN opening its structure would stall the window; only the
    // rows the viewport can show may become nodes.
    @Test
    void tree_fiveThousandUnitBook_holdsAllItemsButRendersFarFewerCells() throws TimeoutException {
        final List<String> hrefs = IntStream.range(0, UNITS_IN_A_HUGE_BOOK)
                .mapToObj(i -> "OEBPS/part-" + i + ".xhtml")
                .toList();
        final int[] oneSegmentEach =
                IntStream.generate(() -> 1).limit(UNITS_IN_A_HUGE_BOOK).toArray();
        openBookThenShowStructure(
                dir.resolve("huge.epub"), BookFixtures.book("huge", BookFormat.EPUB, hrefs, oneSegmentEach));

        assertThat(tree().getRoot().getChildren()).hasSize(UNITS_IN_A_HUGE_BOOK);
        assertThat(renderedCells()).isNotEmpty().hasSizeLessThan(FAR_FEWER_THAN_ALL);
        assertThat(((Label) required("structure-total")).getText()).isEqualTo("5,000 segments in total");
    }

    // IF the screen offered to include, exclude, rename or reorder a unit, THEN it would promise a choice nothing
    // downstream reads.
    @Test
    void card_bookOpened_holdsNoButtonCheckboxToggleOrTextFieldAndTheTreeIsNotEditable() throws TimeoutException {
        openBookThenShowStructure(dir.resolve("book.epub"), elevenUnitBook());

        assertThat(descendantsOf("structure-card"))
                .noneMatch(node -> node instanceof ButtonBase)
                .noneMatch(node -> node instanceof ToggleSwitch)
                .noneMatch(node -> node instanceof TextInputControl)
                .noneMatch(node -> node instanceof ComboBoxBase);
        assertThat(tree().isEditable()).isFalse();
    }

    // IF the screen showed an empty tree with no book, THEN a person would not learn that a book must be opened first.
    @Test
    void screen_noBookOpen_showsTheSharedNoBookStateAndNoStructure() {
        showStructure();

        assertThat(isShown("nobook-card")).isTrue();
        assertThat(((Label) required("nobook-report")).getText())
                .isEqualTo("You have not opened a book yet. Open one to continue.");
        assertThat(optional("structure-card")).isNull();
        assertThat(optional("structure-tree")).isNull();
        assertThat(optional("structure-back")).isNull();
        assertThat(optional("structure-continue")).isNull();
    }

    // IF the no-book state stayed on show after a book was opened, THEN the screen would say no book is open beside
    // one that is.
    @Test
    void screen_bookOpenedWhileItIsOnShow_swapsTheNoBookStateForTheTree() throws TimeoutException {
        showStructure();
        assertThat(isShown("nobook-card")).isTrue();
        port.on(dir.resolve("book.epub"), Result.ok(elevenUnitBook()));

        openBook(dir.resolve("book.epub"));

        assertThat(isShown("structure-card")).isTrue();
        assertThat(optional("nobook-card")).isNull();
        assertThat(tree().getRoot().getChildren()).hasSize(11);
        assertThat(renderedRows()).hasSize(11);
    }

    // IF Back were not wired, THEN a person could not return to the brief they came from.
    @Test
    void backControl_bookOpened_firingItMovesToTheBookBriefScreen() throws TimeoutException {
        openBookThenShowStructure(dir.resolve("book.epub"), elevenUnitBook());

        onFx(() -> button("structure-back").fire());

        assertThat(currentView()).isEqualTo(ViewNames.BOOK_BRIEF);
    }

    // IF Continue stopped at the inert names-and-style step, THEN the path would end at a wall.
    @Test
    void continueControl_bookOpened_firingItMovesToTheTranslatingScreen() throws TimeoutException {
        openBookThenShowStructure(dir.resolve("book.epub"), elevenUnitBook());

        onFx(() -> button("structure-continue").fire());

        assertThat(currentView()).isEqualTo(ViewNames.TRANSLATING);
    }

    // IF the actions sat inside the card, THEN the card would no longer be the read-only list it is specified as.
    @Test
    void actions_bookOpened_sitOutsideTheStructureCard() throws TimeoutException {
        openBookThenShowStructure(dir.resolve("book.epub"), elevenUnitBook());

        assertThat(isShown("structure-back")).isTrue();
        assertThat(isShown("structure-continue")).isTrue();
        assertThat(descendantsOf("structure-card"))
                .doesNotContain(required("structure-back"), required("structure-continue"));
    }

    // IF the route to the import screen were not wired, THEN a person on an empty structure screen could not go on.
    @Test
    void nobookOpen_noBookOpen_firingItMovesToTheImportScreen() {
        showStructure();

        onFx(() -> button("nobook-open").fire());

        assertThat(currentView()).isEqualTo(ViewNames.IMPORT);
    }
}
