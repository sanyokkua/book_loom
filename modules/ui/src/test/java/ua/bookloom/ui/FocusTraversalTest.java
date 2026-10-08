package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.ui.ConformanceCases.Screen;

/**
 * A keyboard alone can work each workflow screen: pressing Tab over and over reaches every control a person can
 * operate there and comes back round to where it started, so no control is out of reach and none traps the focus.
 */
class FocusTraversalTest extends ConformanceTestBase {

    // Far more presses than any screen has controls: a screen that needs more is either trapping focus or broken.
    private static final int MAX_PRESSES = 400;
    private static final Set<String> SCREENS = Set.of(
            "IMPORT_DETECTED",
            "BOOK_BRIEF",
            "STRUCTURE",
            "NAMES_STYLE",
            "TRANSLATING_RUNNING",
            "TRANSLATING_REVIEW",
            "EXPORT");

    static Stream<Arguments> workflowScreens() {
        return ConformanceCases.SCREENS.stream()
                .filter(screen -> SCREENS.contains(screen.name()))
                .map(screen -> Arguments.of(Named.of(screen.name(), screen)));
    }

    // IF a control could not be reached with Tab, or Tab stopped moving, THEN a keyboard user could not finish the
    // step.
    @ParameterizedTest(name = "{0}")
    @MethodSource("workflowScreens")
    void tab_repeatedly_reachesEveryOperableControlAndComesBackRound(final Screen screen) throws TimeoutException {
        show(screen);
        final List<Node> operable = ThemeTestSupport.onFx(() -> operable(scene.getRoot()));
        final Node start = ThemeTestSupport.onFx(() -> {
            operable.get(0).requestFocus();
            return scene.getFocusOwner();
        });

        // Each owner with its ancestors as they were when it had focus: a row's type and gender are a label that hands
        // focus to a combo box put in its place, so the label is reached when its cell held the focus.
        final Set<Node> visited = new LinkedHashSet<>();
        boolean cameBack = false;
        for (int press = 0; press < MAX_PRESSES && !cameBack; press++) {
            final Node owner = ThemeTestSupport.onFx(this::pressTab);
            visited.addAll(ThemeTestSupport.onFx(() -> chain(owner)));
            cameBack = owner.equals(start);
        }
        final List<String> missed = operable.stream()
                .filter(control -> !visited.contains(control))
                .filter(control -> !(control instanceof Label && visited.contains(control.getParent())))
                .filter(control -> !(control instanceof ToggleButton toggle && groupReached(toggle, visited)))
                .map(FocusTraversalTest::describe)
                .toList();

        assertThat(cameBack)
                .as("Tab comes back to the first control within %d presses", MAX_PRESSES)
                .isTrue();
        assertThat(missed).as("controls Tab never reaches on %s", screen.name()).isEmpty();
    }

    private Node pressTab() {
        Event.fireEvent(
                scene.getFocusOwner(),
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.TAB, false, false, false, false));
        return scene.getFocusOwner();
    }

    /** The controls a person can operate: shown, enabled and taking focus, scroll machinery left out. */
    private static List<Node> operable(final Node root) {
        final List<Node> found = new ArrayList<>();
        for (final Node node : Contrast.shown(root)) {
            if (node instanceof Control control
                    && control.isFocusTraversable()
                    && !control.isDisabled()
                    && !(control instanceof ScrollBar)
                    && !(control instanceof ScrollPane)) {
                found.add(control);
            }
        }
        return found;
    }

    // Tab stops once in a group of toggles (a segmented picker, the filter chips), on its chosen one; the arrow keys
    // move within the group, as in any radio group, so a toggle is reachable when its group was.
    private static boolean groupReached(final ToggleButton toggle, final Set<Node> visited) {
        final ToggleGroup group = toggle.getToggleGroup();
        return group != null && group.getToggles().stream().anyMatch(visited::contains);
    }

    private static List<Node> chain(final Node node) {
        final List<Node> chain = new ArrayList<>();
        for (Node walk = node; walk != null; walk = walk.getParent()) {
            chain.add(walk);
        }
        return chain;
    }

    private static String describe(final Node node) {
        return "%s#%s%s".formatted(node.getClass().getSimpleName(), node.getId(), node.getStyleClass());
    }
}
