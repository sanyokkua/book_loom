package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableView;
import javafx.scene.control.skin.TableColumnHeader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.FitProbe;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;

/**
 * The glossary's seven columns need 862 px at their minimum widths. A table narrower than that — the stacked card of a
 * 960 or 1024 px window, or each of the two side-by-side cards of a wide one — scrolls across with a visible scroll
 * bar, so the Gender column and the remove cross stay reachable; a wider table fits every column with no scroll bar.
 */
class GlossaryTableWidthTest extends TranslatingScreenTestBase {

    private static final double HEIGHT = 700;
    private static final double TOLERANCE = 0.5;
    // One line of a value is about 17 px high, two lines are over this.
    private static final double TWO_LINES = 24;
    private static final GlossaryEntry VICTOR =
            GlossaryEntry.of("e1", "p1", "Victor", "Віктор", TermType.CHARACTER, Gender.MALE, true);

    private TableView<?> showGlossaryAt(final double width, final String language) throws Exception {
        return showGlossaryAt(width, language, List.of(VICTOR));
    }

    private TableView<?> showGlossaryAt(final double width, final String language, final List<GlossaryEntry> entries)
            throws Exception {
        useLocale(Locale.forLanguageTag(language));
        projects.on(BOOK, Result.ok(BookFixtures.frankensteinImport()));
        openImport();
        openBook(BOOK);
        chooseTarget();
        glossary.willAnswer(Result.ok(entries));
        resizeScene(width, HEIGHT);
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
        awaitFx(() -> required("names-style-table").getLayoutBounds().getWidth() > 0);
        return (TableView<?>) required("names-style-table");
    }

    private static Optional<ScrollBar> visibleAcrossBar(final TableView<?> table) {
        return table.lookupAll(".scroll-bar").stream()
                .filter(ScrollBar.class::isInstance)
                .map(ScrollBar.class::cast)
                .filter(bar -> bar.getOrientation() == Orientation.HORIZONTAL && bar.isVisible())
                .findFirst();
    }

    /**
     * A column header drawn outside the table's sides.
     *
     * @param column the header's column
     * @param box the header's box in the scene
     */
    private record Outside(Object column, Bounds box) {}

    // The header of each shown column that lies, wholly or in part, outside the table's sides.
    private static List<Outside> headersOutside(final TableView<?> table) {
        final Bounds sides = table.localToScene(table.getLayoutBounds());
        return table.lookupAll(".column-header").stream()
                .filter(TableColumnHeader.class::isInstance)
                .map(TableColumnHeader.class::cast)
                .filter(header -> header.getTableColumn() != null && header.getWidth() > 0)
                .filter(header -> {
                    final Bounds box = header.localToScene(header.getLayoutBounds());
                    return box.getMinX() < sides.getMinX() - TOLERANCE || box.getMaxX() > sides.getMaxX() + TOLERANCE;
                })
                .map(header -> new Outside(header.getTableColumn(), header.localToScene(header.getLayoutBounds())))
                .toList();
    }

    // IF the fitting policy kept the columns at their minimum widths with no scroll bar, THEN at 960 px the remove
    // cross and part of Gender would be cut off with no way to reach them.
    @ParameterizedTest
    @CsvSource({"944, en", "944, uk", "1008, en", "1008, uk", "1700, en", "1700, uk"})
    void narrowTable_scrolledToTheEnd_showsTheRemoveCross(final double width, final String language) throws Exception {
        final TableView<?> table = showGlossaryAt(width, language);

        final List<Outside> outside = ThemeTestSupport.onFx(() -> {
            final ScrollBar across = visibleAcrossBar(table).orElseThrow();
            across.setValue(across.getMax());
            table.layout();
            final Object removeColumn = table.getVisibleLeafColumns().getLast();
            return headersOutside(table).stream()
                    .filter(header -> header.column().equals(removeColumn))
                    .toList();
        });

        assertThat(outside)
                .as("the remove column's header after scrolling to the end")
                .isEmpty();
    }

    // IF a wide stacked card still scrolled, THEN a person would have to scroll for a table that has room.
    @ParameterizedTest
    @CsvSource({"1264, en", "1264, uk", "1424, en", "1424, uk"})
    void wideTable_shown_fitsEveryColumnWithNoScrollBar(final double width, final String language) throws Exception {
        final TableView<?> table = showGlossaryAt(width, language);

        assertThat(ThemeTestSupport.onFx(() -> visibleAcrossBar(table))).isEmpty();
        assertThat(ThemeTestSupport.onFx(() -> headersOutside(table))).isEmpty();
    }

    // IF the Kind and Gender columns were left at the width of their headings, THEN the values with their origin read
    // "ma…" and "character · bo…" at the window sizes people use, or wrapped to a second line.
    @ParameterizedTest
    @CsvSource({"944, en", "944, uk", "1008, en", "1008, uk", "1264, en", "1264, uk", "1700, en", "1700, uk"})
    void values_withTheirOrigin_areWholeAndStayOnOneLine(final double width, final String language) throws Exception {
        final GlossaryEntry sourced =
                VICTOR.withType(TermType.CHARACTER, Origin.BOOK).withGenderFrom(Gender.FEMALE, Origin.BOOK);
        final TableView<?> table = showGlossaryAt(width, language, List.of(sourced));

        final List<String> cut = ThemeTestSupport.onFx(() -> FitProbe.cutParts(table));
        final List<Double> valueHeights = ThemeTestSupport.onFx(() -> table.lookupAll(".glossary-wraps").stream()
                .filter(Node::isVisible)
                .flatMap(node -> node.lookupAll(".text").stream())
                .map(text -> text.getLayoutBounds().getHeight())
                .toList());

        assertThat(cut).as("labels cut in an ellipsis").isEmpty();
        assertThat(valueHeights)
                .as("the Kind and Gender values' text height")
                .isNotEmpty()
                .allSatisfy(height -> assertThat(height).isLessThan(TWO_LINES));
    }
}
