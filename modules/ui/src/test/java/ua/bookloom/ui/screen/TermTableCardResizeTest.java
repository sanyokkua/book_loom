package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.ui.ThemeTestSupport;

/** The two term tables let every column be resized, the last filling the width, none below its minimum. */
class TermTableCardResizeTest extends TermTableCardTestBase {

    private static final double TOLERANCE = 0.5;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void drag(final String card, final int column, final double to) {
        ThemeTestSupport.onFx(() -> {
            final TableView table = table(card);
            final TableColumn dragged = (TableColumn) table.getColumns().get(column);
            table.getColumnResizePolicy().call(new TableView.ResizeFeatures(table, dragged, to - dragged.getWidth()));
            return null;
        });
    }

    private double width(final String card, final int column) {
        return ThemeTestSupport.onFx(() -> table(card).getColumns().get(column).getWidth());
    }

    // IF the table kept a fixed-width policy, THEN a long term could not be widened and would stay cut.
    @ParameterizedTest
    @CsvSource({"names-style", "recurring"})
    void table_eitherCard_flexesItsLastColumnAndKeepsFortySixPixelRows(final String card) throws Exception {
        showScreen("en", 1024, 700, ROSALIND);

        assertThat(table(card).getColumnResizePolicy()).isSameAs(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        assertThat(table(card).getFixedCellSize()).isEqualTo(46.0);
        assertThat(table(card).getColumns())
                .allSatisfy(column -> assertThat(column.isResizable()).isTrue());
    }

    // IF a minimum were lower, THEN a column could be dragged until its content vanished.
    @ParameterizedTest
    @CsvSource({"0, 140", "1, 96", "2, 170", "3, 110", "4, 64", "5, 36"})
    void glossaryColumns_eachOne_hasItsSpecifiedMinimumWidth(final int column, final double minimum) throws Exception {
        showScreen("en", 1024, 700, ROSALIND);

        assertThat(ThemeTestSupport.onFx(
                        () -> table("names-style").getColumns().get(column).getMinWidth()))
                .isEqualTo(minimum);
    }

    // IF widening Term squeezed Translation below 170, THEN the target field a person types in would be cut.
    @Test
    void termColumn_draggedTo220_staysThereAndTranslationKeepsItsMinimum() throws Exception {
        showScreen("en", 1280, 800, ROSALIND);

        drag("names-style", 0, 220);

        assertThat(width("names-style", 0)).isCloseTo(220, within(TOLERANCE));
        assertThat(width("names-style", 2)).isGreaterThanOrEqualTo(170 - TOLERANCE);
    }

    // IF Locked could be dragged under 64, THEN its switch would be cut in half.
    @Test
    void lockedColumn_draggedTo30_isRefusedAndStaysAtItsMinimum() throws Exception {
        showScreen("en", 1280, 800, ROSALIND);
        drag("names-style", 0, 220);

        drag("names-style", 4, 30);

        assertThat(width("names-style", 4)).isCloseTo(64, within(TOLERANCE));
        assertThat(width("names-style", 0)).isCloseTo(220, within(TOLERANCE));
    }

    // IF the total of the widths outgrew the table, THEN the last column would hang out of the card.
    @ParameterizedTest
    @CsvSource({"names-style", "recurring"})
    void columns_afterLayout_addUpToTheTableWidth(final String card) throws Exception {
        showScreen("en", 1280, 800, ROSALIND);

        final double total = ThemeTestSupport.onFx(() -> table(card).getColumns().stream()
                .mapToDouble(TableColumn::getWidth)
                .sum());

        assertThat(total)
                .isLessThanOrEqualTo(ThemeTestSupport.onFx(() -> table(card).getWidth()) + TOLERANCE);
    }
}
