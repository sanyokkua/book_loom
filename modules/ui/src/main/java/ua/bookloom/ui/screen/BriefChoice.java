package ua.bookloom.ui.screen;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import lombok.extern.slf4j.Slf4j;
import org.controlsfx.control.SegmentedButton;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.control.LayoutClass;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * One segmented choice over the values of an enum, always with exactly one segment selected.
 *
 * <p>A segment is never allowed to shrink below its label, because Ukrainian words are long and a label ending in an
 * ellipsis cannot be chosen with confidence. When the labels together are wider than the room the choice is given, each
 * label with more than one word is put on two lines instead, which is how a narrow window still shows every label whole.
 * The choice's minimum width is the width of its labels on two lines, so a layout that decides from minimum widths
 * (two columns that stack) gives it at least that room whichever form it shows at the moment.
 * Pressing the selected segment again does not clear the choice. A value the view model publishes is written with
 * {@link #show} without being handed back as if the person had picked it.
 *
 * @param <E> the choice's value type
 */
@Slf4j
final class BriefChoice<E> {

    private static final double HELP_SPACING = 4;
    // A label measured to the exact pixel can still be drawn with an ellipsis after the skin snaps its box.
    private static final double SNAP_ALLOWANCE = 1;

    /**
     * One segment: the value it stands for, the words on it and, when it has one, what choosing it does.
     *
     * @param value the value the segment stands for
     * @param label the words on the segment
     * @param help what the option does, shown on hover and under the choice while it is selected; or null
     */
    record Option<E>(E value, MessageKey label, @Nullable MessageKey help) {

        Option(final E value, final MessageKey label) {
            this(value, label, null);
        }
    }

    private final SegmentedButton node;
    private final Map<E, ToggleButton> buttons = new HashMap<>();
    private final Map<ToggleButton, E> values = new HashMap<>();
    private final Map<ToggleButton, String> labels = new LinkedHashMap<>();
    private final Consumer<E> onPick;
    private final Map<E, String> helps = new HashMap<>();
    private final Label help = new Label();
    // True while a value from the view model is written into the segments. FX thread only.
    private boolean applying;

    BriefChoice(final String id, final Messages messages, final List<Option<E>> options, final Consumer<E> onPick) {
        Objects.requireNonNull(id, "id");
        this.onPick = Objects.requireNonNull(onPick, "onPick");
        final ToggleButton[] segments =
                options.stream().map(option -> segment(messages, option)).toArray(ToggleButton[]::new);
        help.getStyleClass().add("hint");
        help.setWrapText(true);
        help.setId(id + "-help");
        this.node = new CompactSegments(segments, labels);
        node.setId(id);
        node.getToggleGroup().selectedToggleProperty().addListener((observed, was, now) -> onToggled(was, now));
    }

    Node node() {
        return node;
    }

    /** The choice with a line under it that says what the selected option does; the choice alone when none has help. */
    Node withHelp() {
        if (helps.isEmpty()) {
            return node;
        }
        final VBox box = new VBox(HELP_SPACING, node, help);
        box.setId(node.getId() + "-box");
        return box;
    }

    void show(final E value) {
        final ToggleButton button = buttons.get(value);
        if (button == null) {
            log.debug("choice {} has no segment for {}", node.getId(), value);
            return;
        }
        applying = true;
        try {
            button.setSelected(true);
        } finally {
            applying = false;
        }
        showHelp(value);
    }

    private void showHelp(final E value) {
        showHelpText(helps.get(value));
    }

    /** Replaces the selected option's line with {@code text}, the reason the choice cannot be used now. */
    void explain(final String text) {
        showHelpText(Objects.requireNonNull(text, "text"));
    }

    private void showHelpText(final @Nullable String text) {
        help.setText(text == null ? "" : text);
        help.setVisible(text != null);
        help.setManaged(text != null);
    }

    private ToggleButton segment(final Messages messages, final Option<E> option) {
        final String text = messages.get(option.label());
        final E value = option.value();
        final ToggleButton button = new ToggleButton(text);
        if (option.help() != null) {
            helps.put(value, messages.get(option.help()));
            Tips.install(messages, button, option.help());
        }
        button.setWrapText(true);
        button.setMinWidth(Region.USE_PREF_SIZE);
        buttons.put(value, button);
        values.put(button, value);
        labels.put(button, text);
        return button;
    }

    /**
     * A segmented button whose minimum width is its labels' width on two lines, and which shows them on two lines
     * while the room its parent gives it is narrower than they need on one ({@link LayoutClass#isNarrow}).
     */
    private static final class CompactSegments extends SegmentedButton {

        private final Map<ToggleButton, String> labels;
        private final Text ruler = new Text();
        private boolean twoLines;

        CompactSegments(final ToggleButton[] segments, final Map<ToggleButton, String> labels) {
            super(segments);
            this.labels = labels;
        }

        @Override
        protected double computeMinWidth(final double height) {
            return width(true);
        }

        // Decided where the room is known, in layout; a changed form lays the segments out again on the next pulse.
        @Override
        protected void layoutChildren() {
            final boolean narrow = LayoutClass.isNarrow(twoLines, room(), width(false));
            if (narrow != twoLines) {
                twoLines = narrow;
                labels.forEach((button, text) -> button.setText(narrow ? onTwoLines(text) : text));
            }
            super.layoutChildren();
        }

        private double room() {
            return getParent() instanceof Region parent
                    ? parent.getWidth()
                            - parent.getInsets().getLeft()
                            - parent.getInsets().getRight()
                    : getWidth();
        }

        private double width(final boolean onTwoLines) {
            double width = getInsets().getLeft() + getInsets().getRight();
            for (final ToggleButton button : getButtons()) {
                final String text = labels.getOrDefault(button, button.getText());
                ruler.setFont(button.getFont());
                ruler.setText(onTwoLines ? onTwoLines(text) : text);
                width += ruler.getLayoutBounds().getWidth()
                        + button.getInsets().getLeft()
                        + button.getInsets().getRight()
                        + SNAP_ALLOWANCE;
            }
            return width;
        }
    }

    private static String onTwoLines(final String text) {
        final int middle = text.length() / 2;
        int split = -1;
        for (int at = text.indexOf(' '); at >= 0; at = text.indexOf(' ', at + 1)) {
            if (split < 0 || Math.abs(at - middle) < Math.abs(split - middle)) {
                split = at;
            }
        }
        return split < 0 ? text : text.substring(0, split) + "\n" + text.substring(split + 1);
    }

    private void onToggled(final @Nullable Toggle was, final @Nullable Toggle now) {
        if (applying) {
            return;
        }
        if (now == null) {
            if (was == null) {
                return;
            }
            log.debug("choice {} keeps its selection: a segment was pressed twice", node.getId());
            applying = true;
            try {
                was.setSelected(true);
            } finally {
                applying = false;
            }
            return;
        }
        final E value = Objects.requireNonNull(values.get(now), "a segment of this choice");
        log.debug("choice {} picked {}", node.getId(), value);
        showHelp(value);
        onPick.accept(value);
    }
}
