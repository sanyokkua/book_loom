package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * A keyboard alone can work the whole import screen of an open book: Tab walks the drop zone's Browse, the tree, the
 * segment filters and list, and the footer, in the order they are on screen, and comes back round with no trap.
 */
class ImportFocusTraversalTest extends ImportBookTestBase {

    // Far more presses than the screen has controls: a screen that needs more is trapping the focus.
    private static final int MAX_PRESSES = 200;

    // In reading order: the drop zone, the tree, then the segment browser, then the way on.
    private static final List<String> IN_ORDER = List.of(
            "import-browse",
            "structure-tree",
            "structure-segments-search",
            "structure-segments-kind",
            "structure-segments",
            "import-cancel",
            "import-continue");

    private Node pressTab() {
        Event.fireEvent(
                scene.getFocusOwner(),
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.TAB, false, false, false, false));
        return scene.getFocusOwner();
    }

    private static List<String> idsAbove(final Node owner) {
        final List<String> ids = new ArrayList<>();
        for (Node walk = owner; walk != null; walk = walk.getParent()) {
            if (walk.getId() != null) {
                ids.add(walk.getId());
            }
        }
        return ids;
    }

    private List<String> walkOnceRound() {
        final Node start = ThemeTestSupport.onFx(() -> {
            required(IN_ORDER.get(0)).requestFocus();
            return scene.getFocusOwner();
        });
        final List<String> firstVisits = new ArrayList<>(ThemeTestSupport.onFx(() -> idsAbove(start)));
        final Set<String> seen = new LinkedHashSet<>(firstVisits);
        boolean cameBack = false;
        for (int press = 0; press < MAX_PRESSES && !cameBack; press++) {
            final Node owner = ThemeTestSupport.onFx(this::pressTab);
            for (final String id : ThemeTestSupport.onFx(() -> idsAbove(owner))) {
                if (seen.add(id)) {
                    firstVisits.add(id);
                }
            }
            cameBack = owner.equals(start);
        }
        return cameBack ? firstVisits : List.of();
    }

    // IF a control of the new screen were skipped or a pair of cards were walked in the wrong order, THEN a keyboard
    // user
    // could not read the structure or reach Continue.
    @Test
    void tab_bookOpened_walksBrowseTreeSegmentsAndFooterInScreenOrderAndComesBackRound() throws TimeoutException {
        openFactsBook();
        awaitFx(() -> segmentRows().size() == 4);

        final List<String> firstVisits = walkOnceRound();

        assertThat(firstVisits)
                .as("Tab came back to Browse within %d presses", MAX_PRESSES)
                .isNotEmpty();
        assertThat(firstVisits.stream().filter(IN_ORDER::contains).toList()).containsExactlyElementsOf(IN_ORDER);
    }

    // IF a control the screen shows were out of Tab's reach, THEN a keyboard user could not operate it.
    @Test
    void tab_bookOpened_reachesEveryOperableControl() throws TimeoutException {
        openFactsBook();
        awaitFx(() -> segmentRows().size() == 4);
        final List<String> operable = ThemeTestSupport.onFx(() -> operableIds(required("import-screen")));

        final List<String> reached = walkOnceRound();

        assertThat(operable).isNotEmpty();
        assertThat(operable.stream().filter(id -> !reached.contains(id)).toList())
                .as("controls Tab never reaches")
                .isEmpty();
    }

    private static List<String> operableIds(final Node root) {
        final List<String> ids = new ArrayList<>();
        collectOperable(root, ids);
        return ids;
    }

    private static void collectOperable(final Node node, final List<String> ids) {
        if (node instanceof Control control
                && control.isFocusTraversable()
                && control.isVisible()
                && !control.isDisabled()
                && !(control instanceof ScrollBar)
                && !(control instanceof ScrollPane)
                && control.getId() != null) {
            ids.add(control.getId());
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectOperable(child, ids));
        }
    }
}
