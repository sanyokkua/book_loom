package ua.bookloom.ui;

import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Labeled;
import javafx.scene.control.TitledPane;
import javafx.scene.text.Text;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Finds the labelled parts of a scene that are drawn smaller than their words need, and cards that leave the window. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FitProbe {

    private static final int ELLIPSIS_DOTS = 3;

    /** Every shown label or button at or below {@code root} whose box is smaller than its text needs; FX thread. */
    public static List<String> cutParts(final Node root) {
        final List<String> cut = new ArrayList<>();
        collect(root, cut);
        return cut;
    }

    private static void collect(final Node node, final List<String> cut) {
        if (!node.isVisible()) {
            return;
        }
        // A titled pane is a container here: its preferred width is its content's, which may stack instead.
        if (node instanceof Labeled labeled && !(node instanceof TitledPane)) {
            if (isShort(labeled)) {
                cut.add(labeled.getId() + " '" + labeled.getText() + "'");
            }
            return;
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collect(child, cut));
        }
    }

    // The skin draws what fits and ends the rest in an ellipsis, so a word is lost exactly when a drawn text ends in
    // an ellipsis the label's text does not have. A one-line label that carries its whole text as a tooltip (Overrun)
    // is cut by design; a button never is.
    private static boolean isShort(final Labeled labeled) {
        final String text = labeled.getText();
        if (text == null || text.isEmpty() || labeled.getLayoutBounds().getWidth() <= 0) {
            return false;
        }
        final boolean isCut = labeled.lookupAll(".text").stream()
                .filter(Text.class::isInstance)
                .map(node -> ((Text) node).getText())
                .anyMatch(drawn -> isEllipsised(drawn, text));
        return isCut
                && (labeled instanceof ButtonBase
                        || !TooltipProbe.tipText(labeled).contains(text));
    }

    private static boolean isEllipsised(final String drawn, final String text) {
        final int ellipsis = drawn.endsWith("…") ? 1 : drawn.endsWith("...") ? ELLIPSIS_DOTS : 0;
        if (ellipsis == 0 || text.equals(drawn)) {
            return false;
        }
        return text.startsWith(drawn.substring(0, drawn.length() - ellipsis).stripTrailing());
    }

    /** The dialog cards of the scene that reach past its edges, with the edge they cross; FX thread. */
    public static List<String> cardsOutside(final Node root, final double width, final double height) {
        final List<String> outside = new ArrayList<>();
        for (final Node card : root.lookupAll(".dialog-card")) {
            final Bounds box = card.localToScene(card.getLayoutBounds());
            if (box.getMinX() < 0 || box.getMinY() < 0 || box.getMaxX() > width || box.getMaxY() > height) {
                outside.add(card.getId() + " " + box);
            }
        }
        return outside;
    }
}
