package ua.bookloom.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.Slider;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.control.ToggleSwitch;
import ua.bookloom.ui.control.Tips;

/**
 * Reads the hover explanation a node carries without looking at how it was attached, and walks a scene for every
 * control a person can operate that has none.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TooltipProbe {

    // The key JavaFX's Tooltip.install files a tooltip under on a node that is not a Control.
    private static final String INSTALLED = "javafx.scene.control.Tooltip";

    /** The tooltip the node itself carries. */
    public static Optional<Tooltip> tipOf(final Node node) {
        if (node instanceof Control control && control.getTooltip() != null) {
            return Optional.of(control.getTooltip());
        }
        return Optional.ofNullable(node.getProperties().get(INSTALLED)).map(Tooltip.class::cast);
    }

    /**
     * The text of the tooltip the node carries, or an empty string when it has none. A row control's tooltip waits for
     * the pointer's first visit ({@link Tips#installOnHover}), and its waiting text counts.
     */
    public static String tipText(final Node node) {
        return Tips.explanationOf(node).orElse("");
    }

    /** The text of the tooltip a column's header carries, or an empty string when it has none. */
    public static String headerTipText(final TableColumn<?, ?> column) {
        return column.getGraphic() == null ? "" : tipText(column.getGraphic());
    }

    /** Describes every operable control under {@code root} whose explanation is missing, blank or a raw key. */
    public static List<String> untipped(final Node root) {
        final List<String> missing = new ArrayList<>();
        collect(root, missing);
        return missing;
    }

    private static void collect(final Node node, final List<String> missing) {
        if (isOperable(node) && !isExplained(node)) {
            missing.add(describe(node));
        }
        if (node instanceof TableView<?> table) {
            table.getColumns().stream()
                    .filter(column -> !isGood(headerTipText(column)))
                    .forEach(column -> missing.add("column " + column.getText()));
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collect(child, missing));
        }
    }

    private static boolean isOperable(final Node node) {
        if (insideComboBox(node)) {
            return false;
        }
        return node instanceof ButtonBase
                || node instanceof ComboBoxBase
                || node instanceof Slider
                || node instanceof ToggleSwitch
                || (node instanceof TextInputControl input && input.isEditable());
    }

    private static boolean insideComboBox(final Node node) {
        for (Node up = node.getParent(); up != null; up = up.getParent()) {
            if (up instanceof ComboBoxBase) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExplained(final Node node) {
        if (isGood(tipText(node))) {
            return true;
        }
        for (Node up = node.getParent(); up != null; up = up.getParent()) {
            if (up instanceof SegmentedButton) {
                return isGood(tipText(up));
            }
        }
        return false;
    }

    private static boolean isGood(final String text) {
        return !text.isBlank() && !text.matches("[A-Za-z.]+\\.tip");
    }

    private static String describe(final Node node) {
        final String text = node instanceof Labeled labeled ? labeled.getText() : "";
        return node.getClass().getSimpleName() + "#" + node.getId() + " '" + text + "'";
    }
}
