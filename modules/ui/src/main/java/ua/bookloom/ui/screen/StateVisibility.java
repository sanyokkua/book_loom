package ua.bookloom.ui.screen;

import java.util.Set;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.scene.Node;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.ui.state.RunState;

/** Shows a node, and gives it room, only while the run is in one of the states that node belongs to. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class StateVisibility {

    /** Binds visibility and layout of {@code node} to the run being in one of {@code states}. */
    static <T extends Node> T shownIn(
            final T node, final ReadOnlyObjectProperty<RunState> state, final Set<RunState> states) {
        node.visibleProperty().bind(Bindings.createBooleanBinding(() -> states.contains(state.get()), state));
        node.managedProperty().bind(node.visibleProperty());
        return node;
    }
}
