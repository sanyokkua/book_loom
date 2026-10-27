package ua.bookloom.ui.screen;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.LiveRowFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.LiveCalls;
import ua.bookloom.ui.state.LiveRow;

/**
 * What the tests of the live card's list and detail share: the rows as the list drew them, the selected row, and a way
 * to publish rows the way the mirror does. A row's cell holds a state marker (its word), the locator and one line of
 * text, in the nodes whose style classes are {@code state-marker}, {@code live-row-locator} and {@code live-row-text}.
 */
abstract class LiveCardTestBase extends TranslatingScreenTestBase {

    static final String ROWS = "translating-live-rows";
    static final String DETAIL = "translating-live-detail";

    void showRows(final List<LiveRow> rows) {
        showLive(LiveRowFixtures.calls(null, rows));
    }

    void publishRows(final List<LiveRow> rows) {
        publishCalls(LiveRowFixtures.calls(null, rows));
    }

    void publishCalls(final LiveCalls calls) {
        mirror().live().publishCalls(calls);
        WaitForAsyncUtils.waitForFxEvents();
        onFx(() -> {});
        WaitForAsyncUtils.waitForFxEvents();
    }

    void chooseMode(final boolean detailed) {
        onFx(() -> ((ToggleButton) required("translating-live-mode-" + (detailed ? "detailed" : "simple"))).fire());
        WaitForAsyncUtils.waitForFxEvents();
    }

    @SuppressWarnings("unchecked")
    ListView<LiveRow> rowList() {
        return (ListView<LiveRow>) required(ROWS);
    }

    /** The cells the list drew, in row order. */
    List<ListCell<LiveRow>> cells() {
        WaitForAsyncUtils.waitForFxEvents();
        return nodesUnder(rowList())
                .filter(node -> node instanceof ListCell<?>)
                .map(node -> {
                    @SuppressWarnings("unchecked")
                    final ListCell<LiveRow> cell = (ListCell<LiveRow>) node;
                    return cell;
                })
                .filter(cell -> cell.getItem() != null && cell.isVisible())
                .sorted(Comparator.comparingInt(ListCell::getIndex))
                .toList();
    }

    /** The marker word each drawn row shows, such as "translated" or "translated again alone". */
    List<String> markerWords() {
        return ThemeTestSupport.onFx(() -> cells().stream()
                .map(cell -> ((Labeled) cell.lookup(".state-marker")).getText())
                .toList());
    }

    /** The locators of the drawn rows, in order. */
    List<String> locators() {
        return ThemeTestSupport.onFx(() -> cells().stream()
                .map(cell -> ((Labeled) cell.lookup(".live-row-locator")).getText())
                .toList());
    }

    String selectedId() {
        return ThemeTestSupport.onFx(() -> {
            final LiveRow row = rowList().getSelectionModel().getSelectedItem();
            return row == null ? "" : row.segmentId();
        });
    }

    /** What a person's click on the row at {@code index} does to the list's selection. */
    void pick(final int index) {
        onFx(() -> rowList().getSelectionModel().select(index));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static Stream<Node> nodesUnder(final Node root) {
        return Stream.concat(
                Stream.of(root),
                root instanceof Parent parent
                        ? parent.getChildrenUnmodifiable().stream().flatMap(LiveCardTestBase::nodesUnder)
                        : Stream.empty());
    }
}
