package ua.bookloom.ui.screen;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.text.Text;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.BookStats;
import ua.bookloom.api.document.BookStats.Formatting;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.StructureNode;
import ua.bookloom.api.pipeline.BookPlan;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.RoundTripReport;
import ua.bookloom.api.pipeline.SegmentPreview;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.StructureSegmentRow;

/**
 * What the tests of the import screen that holds the book's structure share: one invented book whose every statistic
 * has its own figure (so a figure shown twice, or under the wrong caption, cannot hide behind an equal number), the
 * scripted checks and segment listings it is opened with, and lookups of where a part sits in the scene.
 */
abstract class ImportBookTestBase extends StructureScreenTestBase {

    static final String LONG_TEXT = "It was a dark and stormy night; the rain fell in torrents, except at occasional "
            + "intervals, when it was checked by a violent gust of wind which swept up the streets.";

    /** The checks' answer for a faithful book: structure and identifiers preserved. */
    static final RoundTripReport FAITHFUL = new RoundTripReport(true, true, List.of(), 5, 5);

    @TempDir
    Path dir;

    /** Two chapters of four and one segments, every statistic distinct: 5 segments, 7 images, 3 code, 2 fonts, 12 verse. */
    static ImportedBook factsBook() {
        return BookFixtures.structured(
                "p1",
                BookFormat.EPUB,
                List.of(
                        new StructureNode("Chapter 1", "unit-0", 4, List.of()),
                        new StructureNode("Chapter 2", "unit-1", 1, List.of())),
                new BookStats(5, 78_214, 7, 3, 2, 12, 4, 1, Set.of(Formatting.ITALICS, Formatting.QUOTES)));
    }

    private static SegmentPreview preview(
            final String id,
            final String locator,
            final SegmentKind kind,
            final String text,
            final int chunk,
            final int chunks) {
        return new SegmentPreview(id, locator, kind, text, text, 5, false, false, chunk, chunks, false);
    }

    /** Scripts what the checks and the segment browser ask the service for. */
    void scriptChecksAndSegments(final RoundTripReport report, final BookPlan plan) {
        projects.onRoundTrip(Result.ok(report));
        projects.onPlan(Result.ok(plan));
        projects.onSegments(
                "unit-0",
                Result.ok(List.of(
                        preview("s0", "ch1 · p01", SegmentKind.HEADING, "Chapter One", 1, 2),
                        preview("s1", "ch1 · p02", SegmentKind.PARAGRAPH, LONG_TEXT, 1, 2),
                        preview("s2", "ch1 · p03", SegmentKind.PARAGRAPH, "Second paragraph.", 2, 2),
                        preview("s3", "ch1 · p04", SegmentKind.PARAGRAPH, "Third paragraph.", 2, 2))));
        projects.onSegments(
                "unit-1",
                Result.ok(List.of(preview("t0", "ch2 · p01", SegmentKind.PARAGRAPH, "The second chapter.", 1, 1))));
    }

    /** Opens {@link #factsBook()} on the import screen and returns as soon as the book is shown. */
    void openFactsBook() throws TimeoutException {
        scriptChecksAndSegments(FAITHFUL, new BookPlan(Map.of("unit-0", 3), List.of()));
        openFactsBookWithoutWaiting();
    }

    /** Opens {@link #factsBook()} and waits for the background checks to answer with this report and plan. */
    void openCheckedFactsBook() throws TimeoutException {
        openCheckedFactsBook(FAITHFUL, new BookPlan(Map.of("unit-0", 3), List.of()));
    }

    void openCheckedFactsBook(final RoundTripReport report, final BookPlan plan) throws TimeoutException {
        scriptChecksAndSegments(report, plan);
        openFactsBookWithoutWaiting();
        awaitChecksFinished();
    }

    /** Opens {@link #factsBook()} with whatever the caller scripted (a held check, say) and does not wait for the checks. */
    void openFactsBookWithoutWaiting() throws TimeoutException {
        final Path source = dir.resolve("book.epub");
        projects.on(source, Result.ok(factsBook()));
        openImport();
        openBook(source);
    }

    /** The label texts at or below the nodes with these ids whose text contains {@code fragment}, ignoring case. */
    List<String> labelsContaining(final String fragment, final String... rootIds) {
        return ThemeTestSupport.onFx(() -> {
            final List<String> found = new ArrayList<>();
            for (final String rootId : rootIds) {
                collectLabels(required(rootId), fragment.toLowerCase(Locale.ROOT), found);
            }
            return found;
        });
    }

    private static void collectLabels(final Node node, final String lower, final List<String> found) {
        if (node instanceof Labeled labeled
                && labeled.getText() != null
                && labeled.getText().toLowerCase(Locale.ROOT).contains(lower)) {
            found.add(labeled.getText());
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectLabels(child, lower, found));
        }
    }

    @SuppressWarnings("unchecked")
    ListView<StructureSegmentRow> segmentList() {
        return (ListView<StructureSegmentRow>) required("structure-segments");
    }

    /** The locator, kind, text and chunk of each filled row of the segment list, in list order. */
    List<List<String>> segmentRows() {
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
        return cells.stream()
                .map(cell -> List.of(
                        cellText(cell, ".structure-seg-locator"),
                        cellText(cell, ".structure-seg-kind"),
                        cellText(cell, ".structure-seg-text"),
                        cellText(cell, ".structure-seg-chunk")))
                .toList();
    }

    private static String cellText(final Node cell, final String selector) {
        return ThemeTestSupport.onFx(() -> ((Label) cell.lookup(selector)).getText());
    }

    void pickSegment(final int index) {
        onFx(() -> segmentList().getSelectionModel().select(index));
    }

    void filterSegments(final String text) {
        onFx(() -> ((TextField) required("structure-segments-search")).setText(text));
    }

    /** The preview's text with its placeholder chips left out, read from the text nodes it drew. */
    String previewText() {
        return ThemeTestSupport.onFx(() -> {
            final StringBuilder plain = new StringBuilder();
            required("structure-preview-text")
                    .lookupAll(".flow-text")
                    .forEach(node -> plain.append(((Text) node).getText()));
            return plain.toString();
        });
    }
}
