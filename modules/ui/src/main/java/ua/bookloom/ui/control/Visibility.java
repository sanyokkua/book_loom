package ua.bookloom.ui.control;

import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Shows a node and gives it room together, so a hidden node never leaves an empty gap in its layout. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Visibility {

    /** Shows or hides {@code node} now, taking no room while hidden. */
    public static void show(final Node node, final boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    /**
     * Shows {@code node} only while {@code shown} holds, taking no room otherwise.
     *
     * @return the same node, for building it inline
     */
    public static <T extends Node> T bind(final T node, final ObservableValue<Boolean> shown) {
        node.visibleProperty().bind(shown);
        node.managedProperty().bind(node.visibleProperty());
        return node;
    }
}
