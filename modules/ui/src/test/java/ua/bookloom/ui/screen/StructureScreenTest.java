package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TreeItem;
import javafx.scene.text.Text;
import org.controlsfx.control.ToggleSwitch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.BookProfile;
import ua.bookloom.api.document.BookStats;
import ua.bookloom.api.document.StructureNode;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.ViewNames;

/**
 * The structure screen's tree, read from the real scene: the book's nodes with their count pills, the nesting, the
 * localized {@code Untitled}, the total beneath it, the bounded number of rows a huge book materialises, and the state
 * with no book open. Row texts are read from the rendered cells, never from the model. The statistics and the checks
 * beside the tree are proven in {@link StructureChecksScreenTest}.
 */
class StructureScreenTest extends StructureScreenTestBase {

    private static final List<List<String>> ELEVEN_ROWS = List.of(
            List.of("Chapter 1", "120"),
            List.of("Chapter 2", "95"),
            List.of("Chapter 3", "130"),
            List.of("Chapter 4", "110"),
            List.of("Chapter 5", "100"),
            List.of("Chapter 6", "115"),
            List.of("Chapter 7", "105"),
            List.of("Chapter 8", "125"),
            List.of("Chapter 9", "135"),
            List.of("Chapter 10", "90"),
            List.of("Chapter 11", "115"));

    private static final int UNITS_IN_A_HUGE_BOOK = 5_000;
    private static final int FAR_FEWER_THAN_ALL = 200;

    @TempDir
    private Path dir;

    /** Eleven top-level nodes whose segment counts add up to 1,240. */
    private static final String LONG_TITLE =
            "Chapter Three: The Law of Universal Gravitation and the Formula Newton Wrote for It";

    private static ImportedBook elevenUnitBook() {
        return BookFixtures.imported(
                "eleven", BookFormat.EPUB, null, null, null, 120, 95, 130, 110, 100, 115, 105, 125, 135, 90, 115);
    }

    // IF a row were dropped, reordered or given a neighbour's count, THEN the person would misread how the book parsed.
    @Test
    void tree_elevenUnitBook_rendersElevenRowsInOrderEachWithItsOwnCount() throws TimeoutException {
        openBookThenShowStructure(dir.resolve("book.epub"), elevenUnitBook());

        assertThat(renderedRows()).containsExactlyElementsOf(ELEVEN_ROWS);
    }

    // IF a long chapter name were cut in its middle ("Chapter Three: ...Gravity Formula"), THEN the person could not
    // read
    // which chapter a row is; at the window minimum the name wraps whole onto a taller row and is its own hover text.
    @Test
    void row_longChapterNameAtTheMinimumWidth_wrapsWholeOntoATallerRow() throws TimeoutException {
        openBookThenShowStructure(
                dir.resolve("book.epub"), bookOf(BookFormat.EPUB, 27, node("Notes", 4), node(LONG_TITLE, 23)));

        resizeScene(CONTENT_AT_MINIMUM_WIDTH, CONTENT_AT_MINIMUM_HEIGHT);

        final List<Label> titles = ThemeTestSupport.onFx(() -> renderedCells().stream()
                .filter(cell -> cell.getItem() != null)
                .sorted(java.util.Comparator.comparingInt(cell -> cell.getIndex()))
                .map(cell -> (Label) cell.lookup(".structure-row-title"))
                .toList());
        assertThat(titles).hasSize(2);
        final Label longOne = titles.get(1);
        assertThat(ThemeTestSupport.onFx(() -> drawn(longOne))).isEqualTo(LONG_TITLE);
        assertThat(ThemeTestSupport.onFx(() -> longOne.getHeight()))
                .isGreaterThan(ThemeTestSupport.onFx(() -> titles.get(0).getHeight()) * 1.5);
        assertThat(TooltipProbe.tipText(longOne)).isEqualTo(LONG_TITLE);
    }

    private static String drawn(final Label label) {
        return label.getChildrenUnmodifiable().stream()
                .filter(Text.class::isInstance)
                .map(node -> ((Text) node).getText().replace("\n", " "))
                .collect(java.util.stream.Collectors.joining());
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

    // IF a row showed anything but the node's own title and count, THEN the screen would invent structure.
    @Test
    void row_singleNode_showsItsTitleAndItsCountAndNothingElse() throws TimeoutException {
        final ImportedBook book = BookFixtures.imported("one", BookFormat.EPUB, null, null, null, 1);
        openBookThenShowStructure(dir.resolve("book.epub"), book);

        assertThat(renderedRows()).hasSize(1);
        assertThat(renderedRows().get(0)).containsExactly("Chapter 1", "1");
    }

    // IF a node without a title showed an empty row, THEN the person could not tell the row from a gap.
    @Test
    void row_nodeWithNoTitle_showsUntitled() throws TimeoutException {
        final ImportedBook titled = BookFixtures.imported("one", BookFormat.EPUB, null, null, null, 3);
        final BookProfile profile = Objects.requireNonNull(titled.profile());
        final ImportedBook book = new ImportedBook(
                titled.projectId(),
                titled.inspection(),
                new BookProfile(
                        null,
                        null,
                        null,
                        List.of(new StructureNode("", null, 3, List.of())),
                        profile.stats(),
                        profile.resourceIds()),
                titled.brief());
        openBookThenShowStructure(dir.resolve("book.epub"), book);

        assertThat(renderedRows().get(0)).containsExactly("Untitled", "3");
    }

    private static StructureNode node(final String title, final int segments, final StructureNode... children) {
        return new StructureNode(title, null, segments, List.of(children));
    }

    private static ImportedBook bookOf(final BookFormat format, final int total, final StructureNode... roots) {
        return BookFixtures.structured(
                "p1", format, List.of(roots), new BookStats(total, 0, 0, 0, 0, 0, 0, 0, Set.of()));
    }

    // IF the tree showed a file name where the book's navigation gives a chapter title, THEN the person would not
    // recognise their chapters.
    @Test
    void tree_epubNavigationTitles_showLettersAndChaptersFirstWithTheirCounts() throws TimeoutException {
        openBookThenShowStructure(
                dir.resolve("book.epub"),
                bookOf(BookFormat.EPUB, 12, node("Letter 1", 2), node("Chapter 1", 4), node("Chapter 2", 6)));

        assertThat(renderedRows())
                .containsExactly(List.of("Letter 1", "2"), List.of("Chapter 1", "4"), List.of("Chapter 2", "6"));
    }

    // IF a unit no navigation entry names showed no row, THEN the person would lose its segments from the picture.
    @Test
    void tree_unitWithNeitherEntryNorHeading_showsItsFileName() throws TimeoutException {
        openBookThenShowStructure(
                dir.resolve("book.epub"), bookOf(BookFormat.EPUB, 5, node("Chapter 8", 2), node("ch09.xhtml", 3)));

        assertThat(renderedRows()).containsExactly(List.of("Chapter 8", "2"), List.of("ch09.xhtml", "3"));
    }

    // IF nested sections were flattened, THEN the screen would state a structure the book does not have.
    @Test
    void tree_fb2PartHoldingTwoChapters_nestsThemAndCountsAllThreeInThePill() throws TimeoutException {
        openBookThenShowStructure(
                dir.resolve("book.fb2"),
                bookOf(BookFormat.FB2, 9, node("Part One", 9, node("Chapter 1", 3), node("Chapter 2", 3))));

        assertThat(tree().getRoot().getChildren()).hasSize(1);
        assertThat(tree().getRoot().getChildren().get(0).getChildren()).hasSize(2);
        assertThat(renderedRows())
                .containsExactly(List.of("Part One", "9"), List.of("Chapter 1", "3"), List.of("Chapter 2", "3"));
    }

    // IF text outside any section or the notes body were left out, THEN the counts would not add up to the book.
    @Test
    void tree_fb2TextOutsideSectionsAndNotesBody_showsUntitledFirstAndNotes() throws TimeoutException {
        openBookThenShowStructure(
                dir.resolve("book.fb2"),
                bookOf(BookFormat.FB2, 10, node("", 2), node("Chapter 1", 7), node("Notes", 1)));

        assertThat(renderedRows())
                .containsExactly(List.of("Untitled", "2"), List.of("Chapter 1", "7"), List.of("Notes", "1"));
    }

    // IF Markdown text before the first heading vanished, THEN its segments would be counted but never shown.
    @Test
    void tree_markdownTextBeforeTheFirstHeading_showsUntitledThenChapterOne() throws TimeoutException {
        openBookThenShowStructure(
                dir.resolve("book.md"), bookOf(BookFormat.MARKDOWN, 5, node("", 1), node("Chapter 1", 4)));

        assertThat(renderedRows()).containsExactly(List.of("Untitled", "1"), List.of("Chapter 1", "4"));
    }

    // IF the title, navigation labels or image descriptions became nodes or were counted, THEN the numbers would not
    // add up to what a reader sees.
    @Test
    void tree_bookWithTitleNavigationLabelsAndAltTexts_showsNoneOfThemAndCountsNone() throws TimeoutException {
        openBookThenShowStructure(
                dir.resolve("book.epub"), bookOf(BookFormat.EPUB, 8, node("Chapter 1", 3), node("Chapter 2", 5)));

        assertThat(renderedRows()).containsExactly(List.of("Chapter 1", "3"), List.of("Chapter 2", "5"));
        assertThat(labelText("structure-total")).isEqualTo("8 segments in total");
    }

    // IF a node that only points into a counted unit showed a zero, THEN the person would read it as an empty chapter.
    @Test
    void row_nodeWithNoCountOfItsOwn_showsItsTitleAndNoPill() throws TimeoutException {
        openBookThenShowStructure(
                dir.resolve("book.epub"),
                BookFixtures.structured(
                        "p1",
                        BookFormat.EPUB,
                        List.of(new StructureNode("Letter 1", "unit-0", null, List.of())),
                        new BookStats(0, 0, 0, 0, 0, 0, 0, 0, Set.of())));

        assertThat(renderedRows()).containsExactly(List.of("Letter 1"));
    }

    // IF the pill were not styled as one, THEN the count would read as part of the title.
    @Test
    void row_bookOpened_countIsALabelWithThePillClass() throws TimeoutException {
        openBookThenShowStructure(dir.resolve("book.epub"), elevenUnitBook());

        assertThat(scene.getRoot().lookupAll(".count-pill")).isNotEmpty().allMatch(pill -> pill instanceof Label);
    }

    // IF the total were not the sum of the rows, THEN the screen would contradict itself.
    @Test
    void total_elevenUnitBook_isTheGroupedSumOfTheRowCounts() throws TimeoutException {
        openBookThenShowStructure(dir.resolve("book.epub"), elevenUnitBook());

        assertThat(renderedCountTexts())
                .containsExactly("120", "95", "130", "110", "100", "115", "105", "125", "135", "90", "115");
        assertThat(((Label) required("structure-total")).getText()).isEqualTo("1,240 segments in total");
    }

    // IF a book with a single segment said "1 segments in total", THEN the plural rule would be wrong at its edge.
    @Test
    void total_singleSegmentBook_usesTheSingularForm() throws TimeoutException {
        final ImportedBook book = BookFixtures.imported("one", BookFormat.TXT, null, null, null, 1);
        openBookThenShowStructure(dir.resolve("book.txt"), book);

        assertThat(((Label) required("structure-total")).getText()).isEqualTo("1 segment in total");
    }

    // IF every row of a huge book were materialised, THEN opening its structure would stall the window; only the
    // rows the viewport can show may become nodes.
    @Test
    void tree_fiveThousandUnitBook_holdsAllItemsButRendersFarFewerCells() throws TimeoutException {
        final int[] oneSegmentEach =
                IntStream.generate(() -> 1).limit(UNITS_IN_A_HUGE_BOOK).toArray();
        openBookThenShowStructure(
                dir.resolve("huge.epub"),
                BookFixtures.imported("huge", BookFormat.EPUB, null, null, null, oneSegmentEach));

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
        projects.on(dir.resolve("book.epub"), Result.ok(elevenUnitBook()));

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

    // IF Continue skipped past names and style, THEN the run would start without the step the reference puts first.
    @Test
    void continueControl_bookOpened_firingItMovesToTheNamesAndStyleScreen() throws TimeoutException {
        openBookThenShowStructure(dir.resolve("book.epub"), elevenUnitBook());

        onFx(() -> button("structure-continue").fire());

        assertThat(currentView()).isEqualTo(ViewNames.NAMES_STYLE);
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
