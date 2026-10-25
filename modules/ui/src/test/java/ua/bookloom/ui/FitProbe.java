package ua.bookloom.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.text.Text;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.controlsfx.control.SegmentedButton;

/** Finds the labelled parts of a scene that are drawn smaller than their words need, and cards that leave the window. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FitProbe {

    private static final int ELLIPSIS_DOTS = 3;
    private static final double TOLERANCE = 0.5;

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
        return isCut && (labeled instanceof ButtonBase || !holdsEveryWord(TooltipProbe.tipText(labeled), text));
    }

    // A tooltip may say the same words in another form — a shortened path's tooltip is the whole path, with its own
    // separators — so it counts when it holds every word of the label's text.
    private static boolean holdsEveryWord(final String tip, final String text) {
        return Arrays.stream(text.split("[\\s▸]+"))
                .filter(word -> !word.isEmpty())
                .allMatch(tip::contains);
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

    /**
     * Every shown control at or below {@code root} that a person cannot see whole across: its right edge runs past the
     * scroll viewport that clips it (or the window, outside any), or past the card it sits on; a screen wider than the
     * content area's viewport counts as well, since its right part is out of sight. FX thread.
     */
    public static List<String> overflowing(final Node root, final double sceneWidth) {
        final List<String> found = new ArrayList<>();
        overflow(root, sceneWidth, sceneWidth, found);
        return found;
    }

    private static void overflow(
            final Node node, final double viewportRight, final double cardRight, final List<String> found) {
        if (!node.isVisible() || isClosedFold(node)) {
            return;
        }
        final Bounds box = node.localToScene(node.getLayoutBounds());
        if (box.getWidth() > 0 && isWatched(node) && box.getMaxX() > Math.min(viewportRight, cardRight) + TOLERANCE) {
            found.add(describe(node) + " right edge " + Math.round(box.getMaxX()) + " > "
                    + Math.round(Math.min(viewportRight, cardRight)));
            return;
        }
        if (node instanceof TextInputControl || node instanceof VirtualFlow<?>) {
            return;
        }
        final double card = node.getStyleClass().contains("card") ? Math.min(cardRight, box.getMaxX()) : cardRight;
        if (node instanceof ScrollPane scroll) {
            scrollContent(scroll, viewportRight, card, found);
            return;
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> overflow(child, viewportRight, card, found));
        }
    }

    // A pane that fits its content to its width must never need to scroll across; one that scrolls across on purpose
    // (a wide table, a code view) clips nothing a person cannot reach.
    private static void scrollContent(
            final ScrollPane scroll, final double viewportRight, final double cardRight, final List<String> found) {
        final Node content = scroll.getContent();
        final Node viewport = scroll.lookup(".viewport");
        if (content == null || viewport == null) {
            return;
        }
        if (!scroll.isFitToWidth() && scroll.getHbarPolicy() != ScrollPane.ScrollBarPolicy.NEVER) {
            return;
        }
        final double right = Math.min(
                viewportRight, viewport.localToScene(viewport.getLayoutBounds()).getMaxX());
        overflow(content, right, cardRight, found);
    }

    // A closed fold keeps its content laid out at full size under a clip of no height; nothing in it is shown.
    private static boolean isClosedFold(final Node node) {
        final Parent parent = node.getParent();
        return parent != null
                && parent.getParent() instanceof TitledPane fold
                && !fold.isExpanded()
                && fold.getContent() != null
                && node.equals(fold.getContent());
    }

    private static boolean isWatched(final Node node) {
        return node instanceof Control || node instanceof Text;
    }

    /**
     * Every shown button, toggle or segmented choice at or below {@code root} drawn narrower than it asks to be: its
     * words are squeezed. A wrapping button laid out tall enough for its lines is whole. A drop-down may be narrower
     * than its longest item: the value it shows is checked as a label, and its arrow by {@link #overflowing}. FX thread.
     */
    public static List<String> squeezed(final Node root) {
        final List<String> found = new ArrayList<>();
        squeeze(root, found);
        return found;
    }

    private static void squeeze(final Node node, final List<String> found) {
        if (!node.isVisible() || isClosedFold(node)) {
            return;
        }
        if (node instanceof Control control && isSqueezable(control) && isSqueezed(control)) {
            found.add(
                    describe(node) + " " + Math.round(control.getWidth()) + " < " + Math.round(control.prefWidth(-1)));
            return;
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> squeeze(child, found));
        }
    }

    private static boolean isSqueezable(final Control control) {
        return control instanceof ButtonBase || control instanceof SegmentedButton;
    }

    private static boolean isSqueezed(final Control control) {
        if (control.getWidth() <= 0 || control.getWidth() + TOLERANCE >= control.prefWidth(-1)) {
            return false;
        }
        return !(control instanceof Labeled labeled
                && labeled.isWrapText()
                && control.getHeight() + TOLERANCE >= control.prefHeight(control.getWidth()));
    }

    /**
     * Every shown button at or below {@code root} that a scroll viewport shows only in part: its top or bottom edge
     * lies across the viewport's edge, so it is drawn half hidden. A button wholly out of view is reached by scrolling
     * and is not listed. FX thread.
     */
    public static List<String> halfHidden(final Node root) {
        final List<String> found = new ArrayList<>();
        for (final Node node : root.lookupAll(".scroll-pane")) {
            if (node instanceof ScrollPane scroll && scroll.isVisible() && scroll.lookup(".viewport") != null) {
                final Node viewport = scroll.lookup(".viewport");
                final Bounds view = viewport.localToScene(viewport.getLayoutBounds());
                scroll.lookupAll(".button").stream()
                        .filter(Node::isVisible)
                        .filter(button -> isAcrossEdge(button.localToScene(button.getLayoutBounds()), view))
                        .forEach(button -> found.add(describe(button)));
            }
        }
        return found;
    }

    private static boolean isAcrossEdge(final Bounds box, final Bounds view) {
        final boolean acrossBottom =
                box.getMinY() < view.getMaxY() - TOLERANCE && box.getMaxY() > view.getMaxY() + TOLERANCE;
        final boolean acrossTop =
                box.getMinY() < view.getMinY() - TOLERANCE && box.getMaxY() > view.getMinY() + TOLERANCE;
        return acrossBottom || acrossTop;
    }

    private static String describe(final Node node) {
        final String text = node instanceof Labeled labeled
                ? " '" + labeled.getText() + "'"
                : node instanceof Text shown ? " '" + shown.getText() + "'" : "";
        Node named = node;
        while (named != null && named.getId() == null) {
            named = named.getParent();
        }
        final String owner = named == null || named.equals(node) ? "" : " in #" + named.getId();
        return node.getClass().getSimpleName() + (node.getId() == null ? "" : "#" + node.getId()) + text + owner;
    }
}
