package ua.bookloom.ui.screen;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeView;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.StructureRow;

/**
 * What the structure screen tests share, on top of the import screen's scripted port and lookups: showing the screen
 * afresh (always from another view, because navigating to the current one is refused), opening a book and then
 * showing it, and reading rows back from the cells the tree actually rendered rather than from its model.
 */
abstract class StructureScreenTestBase extends ImportScreenTestBase {

    /** Shows the structure screen afresh, the way a person arrives from another screen. */
    void showStructure() {
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.STRUCTURE));
    }

    /** Opens {@code document} from {@code source} through the import screen's view model, then shows the screen. */
    void openBookThenShowStructure(final Path source, final Document document) throws TimeoutException {
        port.on(source, Result.ok(document));
        openImport();
        openBook(source);
        showStructure();
    }

    /** The tree the screen draws its rows in. */
    @SuppressWarnings("unchecked")
    TreeView<StructureRow> tree() {
        return (TreeView<StructureRow>) required("structure-tree");
    }

    /** Every {@code .tree-cell} the scene currently holds, filled or not, exactly as the tree materialised them. */
    List<TreeCell<?>> renderedCells() {
        WaitForAsyncUtils.waitForFxEvents();
        final List<TreeCell<?>> cells = new ArrayList<>();
        scene.getRoot().lookupAll(".tree-cell").forEach(node -> cells.add((TreeCell<?>) node));
        return cells;
    }

    /** The texts each filled cell shows, one list per row, in the order the rows are on screen. */
    List<List<String>> renderedRows() {
        return renderedCells().stream()
                .filter(Node::isVisible)
                .filter(cell -> cell.getItem() != null)
                .sorted(Comparator.comparingInt(cell -> cell.getIndex()))
                .map(cell -> labelTexts(cell))
                .toList();
    }

    /** The last text of each rendered row, which is the segment count the row shows. */
    List<String> renderedCountTexts() {
        return renderedRows().stream().map(row -> row.get(row.size() - 1)).toList();
    }

    /** Every node at or below the node with this id, the node itself excluded. */
    List<Node> descendantsOf(final String id) {
        final List<Node> nodes = new ArrayList<>();
        collect(required(id), nodes);
        return nodes;
    }

    /** The view the navigator shows now. */
    ViewNames currentView() {
        return ThemeTestSupport.onFx(() -> navigator.currentView().get());
    }

    /**
     * The text of each label at or below {@code root}; a label's skin holds a text node of its own, which is not a row
     * text.
     */
    private static List<String> labelTexts(final Node root) {
        final List<Node> nodes = new ArrayList<>();
        nodes.add(root);
        collect(root, nodes);
        return nodes.stream()
                .filter(node -> node instanceof Labeled labeled && labeled.getText() != null)
                .map(node -> ((Labeled) node).getText())
                .toList();
    }

    private static void collect(final Node node, final List<Node> nodes) {
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> {
                nodes.add(child);
                collect(child, nodes);
            });
        }
    }
}
