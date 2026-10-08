package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.pipeline.SegmentPreview;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.state.StructureSegmentRow;

/**
 * The structure screen's segment browser, read from the real scene: picking a chapter lists its segments with their
 * chunk badges, picking a row shows its whole text, the filters narrow the list, and the rows have one fixed height.
 */
class StructureSegmentsScreenTest extends StructureScreenTestBase {

    private static final double ROW_HEIGHT = 34;
    private static final String LONG_TEXT =
            "It was a dark and stormy night; the rain fell in torrents, except at occasional "
                    + "intervals, when it was checked by a violent gust of wind which swept up the streets.";

    @TempDir
    private Path dir;

    private static SegmentPreview preview(
            final String id,
            final String locator,
            final SegmentKind kind,
            final String text,
            final int chunk,
            final int chunks) {
        return new SegmentPreview(id, locator, kind, text, text, 5, false, false, chunk, chunks, false);
    }

    private static List<SegmentPreview> chapterOne() {
        return List.of(
                preview("s0", "ch1 · p01", SegmentKind.HEADING, "Chapter One", 1, 2),
                preview("s1", "ch1 · p02", SegmentKind.PARAGRAPH, LONG_TEXT, 1, 2),
                new SegmentPreview("s2", "ch1 · p03", SegmentKind.PARAGRAPH, "2", "2", 1, true, false, 2, 2, false),
                new SegmentPreview(
                        "s3",
                        "ch1 · p04",
                        SegmentKind.PARAGRAPH,
                        "Bold words here",
                        "⟦g0⟧Bold words⟦g1⟧ here",
                        4,
                        false,
                        false,
                        2,
                        2,
                        false));
    }

    private void openChapterOne() throws TimeoutException {
        projects.onSegments("unit-0", Result.ok(chapterOne()));
        projects.onSegments(
                "unit-1",
                Result.ok(List.of(preview("t0", "ch2 · p01", SegmentKind.PARAGRAPH, "The second chapter.", 1, 1))));
        openBookThenShowStructure(
                dir.resolve("book.epub"),
                BookFixtures.imported("p", ua.bookloom.api.document.BookFormat.EPUB, null, null, null, 4, 1));
        awaitFx(() -> segmentRows().size() > 0);
    }

    @SuppressWarnings("unchecked")
    private ListView<StructureSegmentRow> list() {
        return (ListView<StructureSegmentRow>) required("structure-segments");
    }

    private List<ListCell<?>> filledCells() {
        final List<ListCell<?>> cells = new ArrayList<>();
        ThemeTestSupport.onFx(() -> {
            required("structure-segments").lookupAll(".list-cell").forEach(node -> {
                final ListCell<?> cell = (ListCell<?>) node;
                if (cell.getItem() != null) {
                    cells.add(cell);
                }
            });
            return null;
        });
        cells.sort(Comparator.comparingInt(ListCell::getIndex));
        return cells;
    }

    private List<List<String>> segmentRows() {
        return filledCells().stream()
                .map(cell -> List.of(
                        text(cell, ".structure-seg-locator"),
                        text(cell, ".structure-seg-kind"),
                        text(cell, ".structure-seg-text"),
                        text(cell, ".structure-seg-chunk")))
                .toList();
    }

    private static String text(final Node cell, final String selector) {
        return ThemeTestSupport.onFx(() -> ((Label) cell.lookup(selector)).getText());
    }

    private void pick(final int index) {
        onFx(() -> list().getSelectionModel().select(index));
    }

    private void filterText(final String text) {
        onFx(() -> ((TextField) required("structure-segments-search")).setText(text));
    }

    // IF the screen listed no text, THEN a person could not check how the app cut the book into segments.
    @Test
    void segments_firstChapterIsPickedAtOpening_listsItsRowsWithLocatorKindTextAndChunk() throws TimeoutException {
        openChapterOne();

        assertThat(segmentRows())
                .containsExactly(
                        List.of("ch1 · p01", "Heading", "Chapter One", "1/2"),
                        List.of("ch1 · p02", "Paragraph", LONG_TEXT, "1/2"),
                        List.of("ch1 · p03", "Paragraph", "2", "2/2"),
                        List.of("ch1 · p04", "Paragraph", "Bold words here", "2/2"));
        assertThat(labelText("structure-segments-header")).isEqualTo("4 segments · 2 chunks (planned)");
    }

    // IF picking another chapter kept the old rows, THEN the list would not follow the tree.
    @Test
    void segments_anotherChapterPicked_listsThatChaptersRows() throws TimeoutException {
        openChapterOne();

        onFx(() -> tree().getSelectionModel().select(1));
        awaitFx(() -> segmentRows().size() == 1);

        assertThat(segmentRows()).containsExactly(List.of("ch2 · p01", "Paragraph", "The second chapter.", "1/1"));
        assertThat(projects.segmentCalls()).containsExactly("p/unit-0", "p/unit-1");
    }

    // IF a row showed only its cut-off text, THEN a person could not read what the model is given.
    @Test
    void preview_rowPicked_showsTheWholeTextItsLocatorKindTokensAndChunk() throws TimeoutException {
        openChapterOne();

        pick(1);

        assertThat(ThemeTestSupport.onFx(() -> plainOf("structure-preview-text")))
                .isEqualTo(LONG_TEXT);
        assertThat(labelText("structure-preview-meta")).isEqualTo("ch1 · p02 · Paragraph · 5 tokens · chunk 1 of 2");
    }

    // IF a placeholder token were shown raw, THEN the preview would show markup instead of the book's words.
    @Test
    void preview_rowWithFormatting_showsEachTokenAsAChip() throws TimeoutException {
        openChapterOne();

        pick(3);

        final List<Node> chips =
                ThemeTestSupport.onFx(() -> required("structure-preview-text").lookupAll(".placeholder-chip").stream()
                        .toList());
        assertThat(chips).hasSize(2);
        assertThat(ThemeTestSupport.onFx(() -> plainOf("structure-preview-text")))
                .isEqualTo("Bold words here");
    }

    // IF a numeral were listed like a sentence, THEN a person could not see which segments a run copies unchanged.
    @Test
    void row_keptVerbatim_carriesTheKeptAsIsChip() throws TimeoutException {
        openChapterOne();

        final ListCell<?> numeral = filledCells().get(2);

        assertThat(text(numeral, ".structure-seg-kept")).isEqualTo("kept as is");
        assertThat(ThemeTestSupport.onFx(
                        () -> numeral.lookup(".structure-seg-kept").isVisible()))
                .isTrue();
        assertThat(ThemeTestSupport.onFx(
                        () -> filledCells().get(0).lookup(".structure-seg-kept").isVisible()))
                .isFalse();
    }

    // IF the chunks ran together, THEN a person could not see where one drafting batch ends and the next begins.
    @Test
    void row_firstOfEachChunk_isMarkedAsAChunkStart() throws TimeoutException {
        openChapterOne();

        final List<Boolean> starts = ThemeTestSupport.onFx(() -> filledCells().stream()
                .map(cell -> cell.getStyleClass().contains("chunk-start"))
                .toList());

        assertThat(starts).containsExactly(true, false, true, false);
    }

    // IF a search kept every row, THEN a person could not find a passage in a long chapter.
    @Test
    void filter_text_keepsRowsWhoseTextOrLocatorContainsItIgnoringCase() throws TimeoutException {
        openChapterOne();

        filterText("STORMY");
        awaitFx(() -> segmentRows().size() == 1);

        assertThat(segmentRows().get(0).get(0)).isEqualTo("ch1 · p02");
        assertThat(labelText("structure-segments-shown")).isEqualTo("Showing 1 of 4");

        filterText("p04");
        awaitFx(() -> segmentRows().size() == 1);
        assertThat(segmentRows().get(0).get(2)).isEqualTo("Bold words here");
    }

    // IF the kind filter did nothing, THEN a person could not look at the headings alone.
    @Test
    void filter_kind_keepsOnlyThatKind() throws TimeoutException {
        openChapterOne();

        onFx(() -> kinds().getSelectionModel().select(SegmentKind.HEADING));
        awaitFx(() -> segmentRows().size() == 1);

        assertThat(segmentRows().get(0).get(2)).isEqualTo("Chapter One");
    }

    // IF a filter that matched nothing left a blank list, THEN a person would think the chapter was empty.
    @Test
    void filter_matchesNothing_saysSo() throws TimeoutException {
        openChapterOne();

        filterText("zzz");
        awaitFx(() -> segmentRows().isEmpty());

        assertThat(labelText("structure-segments-state")).isEqualTo("No segment matches the filter.");
    }

    // IF rows had their own heights, THEN scrolling a long chapter would measure every row it passes.
    @Test
    void rows_every_haveTheOneFixedHeight() throws TimeoutException {
        openChapterOne();

        assertThat(ThemeTestSupport.onFx(() -> list().getFixedCellSize())).isEqualTo(ROW_HEIGHT);
        assertThat(ThemeTestSupport.onFx(() -> filledCells().stream()
                        .map(cell -> ((Region) cell).getHeight())
                        .distinct()
                        .toList()))
                .containsExactly(ROW_HEIGHT);
    }

    // IF a failed listing looked like an empty chapter, THEN a person would read "no segments" for "could not read".
    @Test
    void state_listingFails_saysItCouldNotBeListed() throws TimeoutException {
        projects.onSegments("unit-0", Result.err(AppError.of(ErrorCode.internal, "Broken", "No.")));
        openBookThenShowStructure(
                dir.resolve("book.epub"),
                BookFixtures.imported("p", ua.bookloom.api.document.BookFormat.EPUB, null, null, null, 4));

        awaitFx(() -> labelText("structure-segments-state").startsWith("The segments could not"));

        assertThat(labelText("structure-segments-state")).isEqualTo("The segments could not be listed.");
        assertThat(segmentRows()).isEmpty();
    }

    // IF a control had no hover explanation, THEN a person could not tell what the filter or the list is for.
    @Test
    void controls_search_kind_list_andPreview_explainThemselves() throws TimeoutException {
        openChapterOne();

        for (final String id : List.of("structure-segments-search", "structure-segments-kind", "structure-segments")) {
            assertThat(TooltipProbe.tipText(required(id))).as(id).isNotBlank();
        }
    }

    @SuppressWarnings("unchecked")
    private ComboBox<SegmentKind> kinds() {
        return (ComboBox<SegmentKind>) required("structure-segments-kind");
    }

    private String plainOf(final String id) {
        final StringBuilder plain = new StringBuilder();
        required(id).lookupAll(".flow-text").forEach(node -> plain.append(((javafx.scene.text.Text) node).getText()));
        return plain.toString();
    }
}
